package dev.nocny.lessive.protocol

/**
 * Candy's CRC-16: polynomial 0x6363, init 0xFFFF, reflected, final XOR 0xFFFF.
 * Written big-endian after the bytes it covers.
 */
object Crc16 {
    private const val POLYNOMIAL = 0x6363

    fun compute(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in from until to) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor POLYNOMIAL else crc ushr 1
            }
        }
        return crc.inv() and 0xFFFF
    }
}
