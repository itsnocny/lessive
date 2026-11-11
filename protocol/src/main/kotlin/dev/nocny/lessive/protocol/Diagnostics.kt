package dev.nocny.lessive.protocol

/**
 * Read-only commands: the machine answers with data and nothing moves. They go through
 * the same unlock → write → GPO → acknowledgement path as a START, so they validate the
 * whole chain without using water.
 */
enum class ReadRequest(val action: Int, val payloadLength: Int) {
    ProgramCycleCounters(0x01, 46),
    MainSoftwareVersion(0x07, 9),
    UiSoftwareVersion(0x08, 9),
    ;

    /** Header, then marker 0x80, action, zeroed room for the answer, CRC. */
    fun toRecord(): ByteArray {
        val record = ByteArray(4 + payloadLength)
        record[0] = 0xD4.toByte()
        record[1] = 0x01
        record[2] = payloadLength.toByte()
        record[3] = 0x02
        record[4] = 0x80.toByte()
        record[5] = action.toByte()
        val crc = Crc16.compute(record, from = 4, to = record.size - 2)
        record[record.size - 2] = (crc ushr 8).toByte()
        record[record.size - 1] = crc.toByte()
        return record
    }

    /** The answer bytes, between action and CRC, from the record the machine wrote back. */
    fun answer(response: ByteArray): ByteArray {
        val end = minOf(response.size, 4 + payloadLength) - 2
        return if (end > 6) response.copyOfRange(6, end) else ByteArray(0)
    }
}

object Answers {
    /**
     * One counter per dial position, big-endian. The EEPROM stores count - 1, so an
     * erased cell (FFFF) reads as zero.
     */
    fun programCounters(answer: ByteArray): List<Int> =
        (0 until answer.size / 2).map { i ->
            val raw = ((answer[2 * i].toInt() and 0xFF) shl 8) or (answer[2 * i + 1].toInt() and 0xFF)
            (raw + 1) and 0xFFFF
        }

    /** Three numbers then two characters, concatenated. */
    fun softwareVersion(answer: ByteArray): String? {
        if (answer.size < 5) return null
        return "${answer[0]}${answer[1]}${answer[2]}${answer[3].toInt().toChar()}${answer[4].toInt().toChar()}"
    }
}
