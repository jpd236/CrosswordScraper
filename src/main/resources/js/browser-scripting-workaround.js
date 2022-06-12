// Kotlin's generated Javascript includes the following code:
// var executeScript_0 = browser.scripting.executeScript;
// which crashes on Manifest v2, since browser.scripting is undefined.
// Define it as an empty object if missing to prevent the crash (it is only used on Manifest v3).
if (!browser.scripting) {
    browser.scripting = {};
}