package browser.webNavigation

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun GetAllFramesDetails(block: GetAllFramesDetails.() -> Unit) = (js("{}") as GetAllFramesDetails).apply(block)