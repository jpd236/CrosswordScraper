package com.jeffpdavidson.crosswordscraper

import browser.tabs.ExecuteScriptDetails
import browser.tabs.QueryInfo
import browser.webNavigation.Frame
import browser.webNavigation.GetAllFramesDetails
import browser.scripting.InjectionTarget
import browser.scripting.ScriptInjection
import kotlinx.coroutines.await
import kotlin.js.Promise

/** Utilities for scraping content from the user's active tab. */
object Scraping {

    data class Frames(val tabId: Int, val frames: List<Frame>)

    /** Get all of the [Frame]s in the user's active tab. */
    suspend fun getAllFrames(): Frames {
        val tabId = browser.tabs.query(QueryInfo { active = true; currentWindow = true }).await()[0].id ?: 0
        val allFrames =
            browser.webNavigation.getAllFrames(GetAllFramesDetails { this.tabId = tabId }).await() ?: arrayOf()

        // Exclude frames whose URLs equal that of their parent frame (if any). In practice, these actually correspond
        // with iframes that have no URL set, and do not contain puzzle data, but would otherwise look like valid
        // puzzle frames if their parent is valid.
        val frameIdToUrlMap = allFrames.associate { it.frameId to it.url }
        val frames = allFrames.filter { it.parentFrameId == -1 || it.url != frameIdToUrlMap[it.parentFrameId] }
            // Sort by frameId, which seems to match the order of the frames on the actual page.
            .sortedBy { it.frameId }
        return Frames(tabId = tabId, frames = frames)
    }

    /**
     * Obtain the output of a Javascript function run inside a frame which returns a string.
     *
     * The extension must hold the host permission corresponding to the frame's URL.
     *
     * @param frameId ID of the frame to pull the global variable from
     * @param function the function to run. Should always return a string.
     * @return the result of the function
     */
    suspend fun executeFunctionForString(tabId: Int, frameId: Int, function: dynamic): String {
        return when (val manifestVersion = browser.runtime.getManifest().manifest_version) {
            2 -> executeFunctionForStringV2(frameId, function)
            3 -> executeFunctionForStringV3(tabId, frameId, function)
            else -> throw IllegalStateException("Unknown manifest version $manifestVersion")
        }.await()
    }

    private fun executeFunctionForStringV2(frameId: Int, function: dynamic): Promise<String> {
        // The popup runs in an isolated context from the scraped frame, so we first need to inject a script to access
        // the frame. In addition, since the script runs in an isolated context, it can only access the DOM and not any
        // variables (see https://developer.chrome.com/docs/extensions/mv3/content_scripts/). So we inject a script into
        // the DOM which inserts a hidden div with the value of the given command, and then read the div's contents from
        // the DOM.
        val functionCode = "${function.toString().replace("\n", "").replace("'", "\\'")}()"
        val divName = "CrosswordScraper-Command"
        val script = """
            var divElem = document.createElement('div');
            divElem.setAttribute('id', '$divName');
            divElem.style.display = 'none';
            document.body.appendChild(divElem);
            var scriptElem = document.createElement('script');
            scriptElem.innerHTML = 'document.getElementById("$divName").textContent = $functionCode';
            document.body.appendChild(scriptElem);
            var data = divElem.textContent;
            document.body.removeChild(scriptElem);
            document.body.removeChild(divElem);
            data;
        """.trimIndent()
        return browser.tabs.executeScript(
            details = ExecuteScriptDetails {
                this.frameId = frameId
                code = script
            }
        ).then {
            it[0] as String
        }
    }

    private fun executeFunctionForStringV3(tabId: Int, frameId: Int, function: dynamic): Promise<String> {
        val injection = ScriptInjection {
            target = InjectionTarget {
                this.tabId = tabId
                frameIds = arrayOf(frameId)
            }
            func = function
            world = "MAIN"
        }
        return browser.scripting.executeScript(injection).then {
            it[0].result as String
        }
    }
}