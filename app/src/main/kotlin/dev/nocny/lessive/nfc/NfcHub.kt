package dev.nocny.lessive.nfc

import android.nfc.Tag
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Bridges reader-mode callbacks (binder thread) to coroutines. */
object NfcHub {
    enum class Availability { Missing, Disabled, Ready }

    private val _tags = MutableSharedFlow<Tag>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val tags: SharedFlow<Tag> = _tags

    private val _availability = MutableStateFlow(Availability.Ready)
    val availability: StateFlow<Availability> = _availability

    @Volatile private var lastTag: Tag? = null
    @Volatile private var lastSeenAt = 0L

    fun onTagDiscovered(tag: Tag) {
        lastTag = tag
        lastSeenAt = SystemClock.elapsedRealtime()
        _tags.tryEmit(tag)
    }

    /** A tag seen in the last few seconds: the phone may already be resting on the pad. */
    fun recentTag(maxAgeMs: Long = 4000): Tag? =
        lastTag?.takeIf { SystemClock.elapsedRealtime() - lastSeenAt < maxAgeMs }

    fun setAvailability(value: Availability) {
        _availability.value = value
    }
}
