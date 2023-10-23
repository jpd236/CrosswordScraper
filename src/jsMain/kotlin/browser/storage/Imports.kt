@file:JsQualifier("browser.storage")

package browser.storage

import kotlin.js.Promise

external val sync: StorageArea

external interface StorageArea {
    fun get(keys: dynamic = definedExternally): Promise<dynamic>
    fun set(keys: dynamic): Promise<Unit>
    fun clear(): Promise<Unit>
}