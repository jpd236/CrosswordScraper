package com.jeffpdavidson.crosswordscraper

import browser.tabs.QueryInfo
import browser.webNavigation.Frame
import browser.webNavigation.GetAllFramesDetails
import chrome.scripting.InjectionTarget
import chrome.scripting.ScriptInjection
import kotlinx.coroutines.await

/** Utilities for scraping content from the user's active tab. */
object Scraping {

    /** Get all of the [Frame]s in the user's active tab. */
    suspend fun getAllFrames(): List<Frame> {
        val tab = browser.tabs.query(QueryInfo { active = true; currentWindow = true }).await()[0]
        val frames = browser.webNavigation.getAllFrames(GetAllFramesDetails { tabId = tab.id }).await() ?: arrayOf()

        // Exclude frames whose URLs equal that of their parent frame (if any). In practice, these actually correspond
        // with iframes that have no URL set, and do not contain puzzle data, but would otherwise look like valid
        // puzzle frames if their parent is valid.
        val frameIdToUrlMap = frames.associate { it.frameId to it.url }
        return frames
            .filter { it.parentFrameId == -1 || it.url != frameIdToUrlMap[it.parentFrameId] }
            // Sort by frameId, which seems to match the order of the frames on the actual page.
            .sortedBy { it.frameId }
    }

    /**
     * Obtain the output of a Javascript command run inside a frame which returns a string.
     *
     * The extension must hold the host permission corresponding to the frame's URL.
     *
     * @param frameId ID of the frame to pull the global variable from
     * @param script the script to run from resources/contentScripts
     * @return the result of the command, or the empty string if the variable is undefined
     */
    suspend fun executeScriptForString(frameId: Int, script: String): String {
        // TODO: propagate this down
        val tabId = browser.tabs.query(QueryInfo { active = true; currentWindow = true }).await()[0].id
        val injection = ScriptInjection {
            target = InjectionTarget {
                this.tabId = tabId!!
                frameIds = arrayOf(frameId)
            }
            files = arrayOf("contentScripts/$script.js")
        }
        return chrome.scripting.executeScript(injection).then {
            it[0].result as String
        }.catch<String> { t ->
            console.log(t)
            // TODO: Is this still true of the new method?
            // When browser.tabs.executeScript returns a rejected Promise, it passes a plain object with a message, not
            // an Error. This means that "t" isn't actually a Throwable, despite the compile-time type, and if we call
            // await() directly, it won't actually throw a catchable Throwable, but will just abort execution and dump
            // the object to the console. So we wrap the object in an actual Exception for type safety.
            // See: https://youtrack.jetbrains.com/issue/KT-47138
            throw Exception("Error executing command on frame ${frameId}: ${t.message}")
        }.await()
    }
}