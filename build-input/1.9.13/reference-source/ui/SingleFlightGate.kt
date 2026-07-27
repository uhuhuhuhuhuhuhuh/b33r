package com.streamdeck.iptv.ui

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets only one native-player transition run at a time. VLC callbacks can
 * arrive from native threads, so this must be atomic rather than Compose state.
 */
internal class SingleFlightGate {
    private val acquired = AtomicBoolean(false)

    fun tryAcquire(): Boolean = acquired.compareAndSet(false, true)

    fun release() {
        acquired.set(false)
    }
}
