@file:JsQualifier("browser.tabs")

package browser.tabs

import kotlin.js.Promise

external fun create(createInfo: CreateInfo): Promise<Tab>

external interface CreateInfo {
    var url: String?
}

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
