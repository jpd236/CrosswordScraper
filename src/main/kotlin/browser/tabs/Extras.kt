package browser.tabs

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun QueryInfo(block: QueryInfo.() -> Unit) = (js("{}") as QueryInfo).apply(block)

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun ExecuteScriptDetails(block: ExecuteScriptDetails.() -> Unit) =
    (js("{}") as ExecuteScriptDetails).apply(block)
