@file:JsQualifier("chrome.scripting")

package chrome.scripting

import kotlin.js.Promise

external interface InjectionTarget {
    var tabId: Int
    var frameIds: Array<Int>
}

external interface ScriptInjection {
    var files: Array<String>
    var target: InjectionTarget
}

external interface InjectionResult {
    val result: Any
}

external fun executeScript(injection: ScriptInjection): Promise<Array<InjectionResult>>
