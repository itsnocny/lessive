package dev.nocny.lessive.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import dev.nocny.lessive.protocol.M24srSession
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NotAWasherTag : Exception("not an ISO-DEP tag")

/**
 * Waits for the washer's tag, opens one IsoDep session and runs [block] on it.
 * A tag lost before [block] finishes surfaces as [IOException].
 */
suspend fun <T> withWasher(log: (String) -> Unit, block: suspend (M24srSession) -> T): T {
    val tag = NfcHub.recentTag()?.takeIf { canConnect(it) } ?: NfcHub.tags.first()
    return withContext(Dispatchers.IO) {
        val isoDep = IsoDep.get(tag) ?: throw NotAWasherTag()
        try {
            isoDep.connect()
            // An M24SR EEPROM write can take longer than the default timeout.
            isoDep.timeout = 4000
            // Short in-session wait: some machines (EY 12102DE) only answer once the phone
            // releases the tag, which the caller does by closing and reconnecting.
            block(M24srSession(IsoDepTransport(isoDep, log), M24srSession.Timings(ackTimeoutMs = 3000)))
        } finally {
            runCatching { isoDep.close() }
        }
    }
}

private suspend fun canConnect(tag: Tag): Boolean = withContext(Dispatchers.IO) {
    val isoDep = IsoDep.get(tag) ?: return@withContext false
    try {
        isoDep.connect()
        true
    } catch (_: IOException) {
        false
    } finally {
        runCatching { isoDep.close() }
    }
}
