package dev.nocny.lessive.protocol

/** ISO 7816-4 commands for the ST M24SR, plus its proprietary GPO control. */
internal object Apdu {
    const val FILE_STATUS = 0x0001
    const val FILE_COMMAND = 0x0002
    const val FILE_SYSTEM = 0xE101

    val SELECT_NDEF_APP = hexToBytes("00 A4 04 00 07 D2 76 00 00 85 01 01 00")

    /** Empty VERIFY: reports the lock state without burning a retry. */
    val VERIFY_PROBE = bytes(0x00, 0x20, 0x00, 0x02, 0x00)

    fun selectFile(id: Int) = bytes(0x00, 0xA4, 0x00, 0x0C, 0x02, id ushr 8, id and 0xFF)

    fun readBinary(offset: Int, length: Int) =
        bytes(0x00, 0xB0, offset ushr 8, offset and 0xFF, length)

    fun updateBinary(offset: Int, data: ByteArray) =
        bytes(0x00, 0xD6, offset ushr 8, offset and 0xFF, data.size) + data

    fun verifyWritePassword(password: ByteArray) =
        bytes(0x00, 0x20, 0x00, 0x02, password.size) + password

    /** ST proprietary (class A2): drive the GPO pin wired to the washer's MCU interrupt. */
    fun gpo(high: Boolean) = bytes(0xA2, 0xD6, 0x00, 0x1F, 0x01, if (high) 1 else 0)
}

/** A response APDU split into data and status word. */
class Response(raw: ByteArray) {
    val data: ByteArray = if (raw.size >= 2) raw.copyOf(raw.size - 2) else ByteArray(0)
    val sw: Int = if (raw.size >= 2) ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or (raw[raw.size - 1].toInt() and 0xFF) else 0
    val ok get() = sw == 0x9000
}
