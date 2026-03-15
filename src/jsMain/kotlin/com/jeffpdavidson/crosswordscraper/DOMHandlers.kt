package com.jeffpdavidson.crosswordscraper

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget

val mainScope = MainScope()

fun EventTarget.onEvent(type: String, listener: (Event) -> Unit) = addEventListener(type, listener)

fun EventTarget.onEventAsync(type: String, listener: suspend (Event) -> Unit) =
    onEvent(type = type, listener = { event: Event -> mainScope.launch { listener(event) } })

fun EventTarget.onContentLoadedEventAsync(listener: suspend (Event) -> Unit) =
    onEventAsync("DOMContentLoaded", listener)
