package browser.tabs

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun CreateInfo(block: CreateInfo.() -> Unit) = (js("{}") as CreateInfo).apply(block)

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun QueryInfo(block: QueryInfo.() -> Unit) = (js("{}") as QueryInfo).apply(block)
