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
    var divName = "CrosswordScraper-Command";
    var script = "var divElem = document.createElement('div');\n" +
        "divElem.setAttribute('id', '" + divName + "');\n" +
        "divElem.style.display = 'none';\n" +
        "document.body.appendChild(divElem);\n" +
        "var scriptElem = document.createElement('script');\n" +
        "scriptElem.innerHTML = 'document.getElementById(\"" + divName + "\").textContent = " + functionCode + "';\n" +
        "document.body.appendChild(scriptElem);\n" +
        "var data = divElem.textContent;\n" +
        "document.body.removeChild(scriptElem);\n" +
        "document.body.removeChild(divElem);\n" +
        "data;";
    return browser.tabs.executeScript(
        {
            frameId: frameId,
            code: script
        }
    ).then(executeResult => executeResult[0]);
}