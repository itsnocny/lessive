package dev.nocny.lessive.nfc

import android.nfc.tech.IsoDep
import dev.nocny.lessive.protocol.ApduTransport
import dev.nocny.lessive.protocol.toHex

/** [ApduTransport] over an open [IsoDep], reporting every exchange to [log]. */
class IsoDepTransport(
    private val isoDep: IsoDep,
    private val log: (String) -> Unit,
) : ApduTransport {
    override fun transceive(command: ByteArray): ByteArray {
        log("→ ${command.toHex()}")
        return try {
            isoDep.transceive(command).also { log("← ${it.toHex()}") }
        } catch (e: Exception) {
            log("✕ ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
