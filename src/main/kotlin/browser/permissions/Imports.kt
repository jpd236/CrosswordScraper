@file:JsQualifier("browser.permissions")

package browser.permissions

import kotlin.js.Promise

external fun contains(permissions: Permissions): Promise<Boolean>

external fun request(permissions: Permissions): Promise<Boolean>

external interface Permissions {
    var origins: Array<String>
}