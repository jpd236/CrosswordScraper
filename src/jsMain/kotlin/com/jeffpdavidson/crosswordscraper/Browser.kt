import kotlinx.browser.window

/** Returns whether the extension is believed to be running on Firefox for Android. */
fun isFirefoxForAndroid(): Boolean {
    val userAgent = window.navigator.userAgent
    return userAgent.contains("\\bMobile\\b".toRegex()) && userAgent.contains("\\bFirefox\\b".toRegex())
}