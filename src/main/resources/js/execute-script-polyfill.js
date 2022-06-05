// Make executeScript accessible on Manifest v3 without crashing in Manifest v2 where it doesn't exist.
var executeScript = chrome.scripting ? chrome.scripting.executeScript : undefined;