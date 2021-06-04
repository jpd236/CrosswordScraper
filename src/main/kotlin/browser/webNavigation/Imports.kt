@file:JsQualifier("browser.webNavigation")

package browser.webNavigation

import kotlin.js.Promise

external interface GetAllFramesDetails {
    var tabId: Int?
}

external interface Frame {
    var frameId: Int
    var parentFrameId: Int
    var url: String
}

external fun getAllFrames(details: GetAllFramesDetails): Promise<Array<Frame>?>
