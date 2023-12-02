// Firefox-specific logic and method implementations.

// Kotlin's generated Javascript includes the following code:
// var executeScript_0 = browser.scripting.executeScript;
// which crashes on Manifest v2, since browser.scripting is undefined.
// Define it as an empty object if missing to prevent the crash (it is only used on Manifest v3).
if (!browser.scripting) {
    browser.scripting = {};
}

// Execute function for Manifest v2.
function browserExecuteFunctionForString(tabId, frameId, fn) {
    // The popup runs in an isolated context from the scraped frame, so we first need to inject a script to access
    // the frame. In addition, since the script runs in an isolated context, it can only access the DOM and not any
    // variables (see https://developer.chrome.com/docs/extensions/mv3/content_scripts/). So we inject a script into
    // the DOM which inserts a hidden div with the value of the given command, and then read the div's contents from
    // the DOM.
    var functionCode = fn.toString().replaceAll("\n", "").replaceAll("\\", "\\\\").replaceAll("'", "\\'") + "()";
    var divName = "CrosswordScraper-Output";
    var scriptName = "CrosswordScraper-Script";
    var outputPlaceholder = "__OUTPUT_PLACEHOLDER__";
    var script = "var divElem = document.createElement('div');\n" +
        "divElem.setAttribute('id', '" + divName + "');\n" +
        "divElem.style.display = 'none';\n" +
        "divElem.textContent = '" + outputPlaceholder + "';\n" +
        "document.body.appendChild(divElem);\n" +
        // Due to https://bugzilla.mozilla.org/show_bug.cgi?id=1267027, if a website sets a Content-Security-Policy,
        // it will block this injected script from executing if the script code is embedded directly on the page. As a
        // workaround, we can put it behind a blob: URL.
        "var scriptElem = document.createElement('script');\n" +
        "scriptElem.setAttribute('id', '" + scriptName + "');\n" +
        "var scriptText = 'document.getElementById(\"" + divName + "\").textContent = " + functionCode + "';\n" +
        "var scriptBlob = new Blob([decodeURIComponent(scriptText)], {type: 'text/javascript'});\n" +
        "var scriptUrl = URL.createObjectURL(scriptBlob);\n" +
        "scriptElem.src = scriptUrl;\n" +
        "document.body.appendChild(scriptElem);\n" +
        "null";
    return (browser.tabs.executeScript(
        {
            frameId: frameId,
            code: script
        }
    ))
    // Unlike direct addition of a text script, using a blob: URL does not immediately run the script. So we poll every
    // 25ms until the placeholder in the div has been replaced, indicating that the script is finished.
    // TODO: Is there a better way to do this via message passing? It seems like we should be able to use
    // window.postMessage and window.addEventListener to send/receive messages between our injected script and the
    // extension, but they seem to be getting dropped. Better yet, whenever Firefox supports the MAIN context in
    // browser.scripting.executeScript, we can just use that and unify implementations with Chrome.
    .then(async function(executeResult) {
        var data = outputPlaceholder;
        var fetchAndCleanupScript =
            "var divElem = document.getElementById('" + divName + "');" +
            "var scriptElem = document.getElementById('" + scriptName + "');" +
            "var data = divElem.textContent;\n" +
            "if (data !== '" + outputPlaceholder + "') {\n" +
            "    URL.revokeObjectURL(scriptElem.src);\n" +
            "    document.body.removeChild(scriptElem);\n" +
            "    document.body.removeChild(divElem);\n" +
            "}\n" +
            "data;";
        while (data === outputPlaceholder) {
            await new Promise((resolve) => setTimeout(resolve, 25));
            var result = await browser.tabs.executeScript(
                {
                    frameId: frameId,
                    code: fetchAndCleanupScript
                }
            );
            data = result[0];
        }
        return data;
    });
}