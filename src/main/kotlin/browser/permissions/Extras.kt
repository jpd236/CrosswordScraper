package browser.permissions

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
inline fun Permissions(block: Permissions.() -> Unit) = (js("{}") as Permissions).apply(block)