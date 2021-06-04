@file:JsQualifier("browser.tabs")

package browser.tabs

import kotlin.js.Promise

external fun query(queryInfo: QueryInfo): Promise<Array<Tab>>

external interface QueryInfo {
    var active: Boolean?
    var currentWindow: Boolean?
    var url: String?
}

external interface Tab {
    val id: Int?
    val url: String?
}

external fun executeScript(tabId: Int? = definedExternally, details: ExecuteScriptDetails): Promise<Array<dynamic>>

external interface ExecuteScriptDetails {
    var code: String?
    var frameId: Int?
}
