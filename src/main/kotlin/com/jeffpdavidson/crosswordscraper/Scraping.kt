package com.jeffpdavidson.crosswordscraper

import browser.tabs.ExecuteScriptDetails
import browser.tabs.QueryInfo
import browser.webNavigation.Frame
import browser.webNavigation.GetAllFramesDetails
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
     * Read a global Javascript string variable from a frame.
     *
     * The extension must hold the host permission corresponding to the frame's URL.
     *
     * @param frameId ID of the frame to pull the global variable from
     * @param varName name of the global variable, e.g. document.title
     * @return the value of the variable, or the empty string if the variable is undefined
     */
    suspend fun readGlobalString(frameId: Int, varName: String): String {
        return executeCommandForString(frameId, "window.$varName === undefined ? \"\" : window.$varName")
    }

    /**
     * Read a global Javascript JSON variable from a frame.
     *
     * The extension must hold the host permission corresponding to the frame's URL.
     *
     * @param frameId ID of the frame to pull the global variable from
     * @param varName name of the global variable, e.g. document.title
     * @return the value of the variable as a JSON string, or the empty string if the variable is undefined
     */
    suspend fun readGlobalJson(frameId: Int, varName: String): String =
        executeCommandForString(frameId, "window.$varName === undefined ? \"\" : JSON.stringify(window.$varName)")

    /**
     * Obtain the output of a Javascript command run inside a frame which returns a string.
     *
     * The extension must hold the host permission corresponding to the frame's URL.
     *
     * @param frameId ID of the frame to pull the global variable from
     * @param command the command to run. Should always return a string. Single quotes must be escaped.
     * @return the result of the command, or the empty string if the variable is undefined
     */
    suspend fun executeCommandForString(frameId: Int, command: String): String {
        // The popup runs in an isolated context from the scraped frame, so we first need to inject a script to access
        // the frame. In addition, since the script runs in an isolated context, it can only access the DOM and not any
        // variables (see https://developer.chrome.com/docs/extensions/mv3/content_scripts/). So we inject a script into
        // the DOM which inserts a hidden div with the value of the given command, and then read the div's contents from
        // the DOM.
        val divName = "CrosswordScraper-Command"
        val script = """
            var divElem = document.createElement('div');
            divElem.setAttribute('id', '$divName');
            divElem.style.display = 'none';
            document.body.appendChild(divElem);
            var scriptElem = document.createElement('script');
            scriptElem.innerHTML = 'document.getElementById("$divName").textContent = $command;';
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
        }.catch<String> { t ->
            // When browser.tabs.executeScript returns a rejected Promise, it passes a plain object with a message, not
            // an Error. This means that "t" isn't actually a Throwable, despite the compile-time type, and if we call
            // await() directly, it won't actually throw a catchable Throwable, but will just abort execution and dump
            // the object to the console. So we wrap the object in an actual Exception for type safety.
            // See: https://youtrack.jetbrains.com/issue/KT-47138
            throw Exception("Error executing command on frame ${frameId}: ${t.message}")
        }.await()
    }
}