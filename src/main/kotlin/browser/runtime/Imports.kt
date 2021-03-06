@file:JsQualifier("browser.runtime")

package browser.runtime

import kotlin.js.Promise

external fun getURL(path: String): String
external fun openOptionsPage(): Promise<Unit>
external fun getManifest(): Manifest

external interface Manifest {
    var manifest_version: Int
    var version: String
}