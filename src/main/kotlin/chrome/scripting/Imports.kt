// We can't import from chrome.scripting directly as it crashes in Manifest v2 mode.
// Instead, we use execute-script-polyfill.js to populate "executeScript" and reference it here.
// file:JsQualifier("chrome.scripting")

package chrome.scripting

import kotlin.js.Promise

external interface InjectionTarget {
    var tabId: Int
    var frameIds: Array<Int>
}

external interface ScriptInjection {
    var func: dynamic
    var target: InjectionTarget
    var world: String
}

external interface InjectionResult {
    val result: Any
}

external fun executeScript(injection: ScriptInjection): Promise<Array<InjectionResult>>
