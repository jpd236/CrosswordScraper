@file:JsQualifier("browser.scripting")

package browser.scripting

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
