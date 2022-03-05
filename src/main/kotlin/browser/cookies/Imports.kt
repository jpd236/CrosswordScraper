@file:JsQualifier("browser.cookies")

package browser.cookies

import kotlin.js.Promise

external fun get(details: Details): Promise<Cookie>

external interface Details {
    var name: String
    var url: String
}

external interface Cookie {
    var value: String
}
