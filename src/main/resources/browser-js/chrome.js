// Chrome-specific logic and method implementations.

// Execute function for Manifest v3.
function browserExecuteFunctionForString(tabId, frameId, fn) {
    var injection = {
        target: {
            tabId: tabId,
            frameIds: [frameId]
        },
        func: fn,
        world: "MAIN"
    };
    return browser.scripting.executeScript(injection).then(executeResult => executeResult[0].result);
}