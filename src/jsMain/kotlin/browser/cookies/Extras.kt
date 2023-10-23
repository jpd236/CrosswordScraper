package browser.cookies

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun Details(block: Details.() -> Unit) = (js("{}") as Details).apply(block)
