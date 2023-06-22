package com.jeffpdavidson.crosswordscraper

import browser.tabs.QueryInfo
import browser.webNavigation.Frame
import browser.webNavigation.GetAllFramesDetails
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
        // TODO(#4): Move back to native implementation once Firefox supports v3. For now, we need this in Javascript so
        // we can strip out the Firefox implementation when building for Chrome, which uses a method that is banned by
        // Chrome in v3 extensions.
        return browserExecuteFunctionForString(tabId, frameId, function).await()
    }
}

external fun browserExecuteFunctionForString(tabId: Int, frameId: Int, function: dynamic): Promise<String>