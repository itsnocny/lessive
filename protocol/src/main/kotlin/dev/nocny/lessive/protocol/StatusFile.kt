package dev.nocny.lessive.protocol

/**
 * Contents of the read-only status file 0001: a URI record and a record holding
 * ASCII digits followed by a CRC.
 */
data class StatusFile(
    val raw: ByteArray,
    val uri: String?,
    val digits: String?,
    val crcValid: Boolean,
) {
    /**
     * The first 16 digits are the machine's appliance code.
     */
    val applianceCode: String? get() = digits?.takeIf { it.length >= 16 }?.take(16)
    val extraDigits: String? get() = digits?.takeIf { it.length > 16 }?.drop(16)

    companion object {
        private val URI_PREFIXES = listOf(
            "", "http://www.", "https://www.", "http://", "https://",
        )

        fun parse(message: ByteArray): StatusFile {
            val records = Ndef.parse(message)
            val uri = records.firstOrNull { it.tnf == 0x01 && it.typeString == "U" }?.let {
                val prefix = URI_PREFIXES.getOrElse(it.payload.firstOrNull()?.toInt() ?: 0) { "" }
                prefix + it.payload.copyOfRange(1, it.payload.size).decodeToString()
            }
            var digits: String? = null
            var crcValid = false
            for (record in records) {
                val payload = record.payload
                val run = payload.indexOfFirst { it in '0'.code.toByte()..'9'.code.toByte() }
                if (run < 0) continue
                var end = run
                while (end < payload.size && payload[end] in '0'.code.toByte()..'9'.code.toByte()) end++
                if (end - run < 16) continue
                digits = payload.copyOfRange(run, end).decodeToString()
                if (end + 2 <= payload.size) {
                    val stored = ((payload[end].toInt() and 0xFF) shl 8) or (payload[end + 1].toInt() and 0xFF)
                    crcValid = stored == Crc16.compute(payload, run, end)
                }
                break
            }
            return StatusFile(message, uri, digits, crcValid)
        }
    }
}
