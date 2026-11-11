package dev.nocny.lessive.protocol

fun ByteArray.toHex(separator: String = " "): String =
    joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun hexToBytes(hex: String): ByteArray {
    val clean = hex.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "Odd hex length: $hex" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
