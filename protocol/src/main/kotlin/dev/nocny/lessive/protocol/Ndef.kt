package dev.nocny.lessive.protocol

/** Minimal NDEF record parser: enough for the washer's two files. */
data class NdefRecord(val tnf: Int, val type: ByteArray, val payload: ByteArray) {
    val typeString get() = type.decodeToString()
}

object Ndef {
    fun parse(message: ByteArray): List<NdefRecord> {
        val records = mutableListOf<NdefRecord>()
        var i = 0
        while (i < message.size) {
            val header = message[i].toInt() and 0xFF
            val shortRecord = header and 0x10 != 0
            val hasId = header and 0x08 != 0
            val tnf = header and 0x07
            if (i + 2 > message.size) break
            val typeLength = message[i + 1].toInt() and 0xFF
            i += 2
            val payloadLength = if (shortRecord) {
                (message.getOrNull(i) ?: break).toInt() and 0xFF
            } else {
                if (i + 4 > message.size) break
                ((message[i].toInt() and 0xFF) shl 24) or ((message[i + 1].toInt() and 0xFF) shl 16) or
                    ((message[i + 2].toInt() and 0xFF) shl 8) or (message[i + 3].toInt() and 0xFF)
            }
            i += if (shortRecord) 1 else 4
            val idLength = if (hasId) (message.getOrNull(i++) ?: break).toInt() and 0xFF else 0
            val end = i + typeLength + idLength + payloadLength
            if (end > message.size) break
            val type = message.copyOfRange(i, i + typeLength)
            val payload = message.copyOfRange(i + typeLength + idLength, end)
            records += NdefRecord(tnf, type, payload)
            i = end
            if (header and 0x40 != 0) break // ME: message end
        }
        return records
    }
}
