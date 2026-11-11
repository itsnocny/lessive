package dev.nocny.lessive.protocol

import java.io.IOException

/**
 * Behavioural model of the washer's M24SR + MCU, built from the documented captures:
 * file 0002 is password-protected, the A2 GPO command drops the application context
 * (a bare SELECT file then answers 6A82), and the MCU answers a command only after a
 * GPO low→high edge, by rewriting byte [4] 80→00 with a fresh CRC.
 */
class FakeM24sr(
    private val password: ByteArray = ByteArray(16),
    private val statusFile: ByteArray = byteArrayOf(0, 0),
    var mcuAnswers: Boolean = true,
    var refuseSelector: Int? = null,
    var dropAfterCommands: Int? = null,
) : ApduTransport {
    val log = mutableListOf<ByteArray>()
    private var appSelected = false
    private var selectedFile: Int? = null
    private var unlocked = false
    private var gpoHigh = true
    private val commandFile = ByteArray(0x80)
    var acknowledged = false
        private set

    override fun transceive(command: ByteArray): ByteArray {
        log += command
        dropAfterCommands?.let { if (log.size > it) throw IOException("Tag was lost.") }
        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF
        return when {
            cla == 0x00 && ins == 0xA4 && command[2].toInt() == 0x04 -> {
                appSelected = command.copyOfRange(5, 12).contentEquals(hexToBytes("D2760000850101"))
                selectedFile = null
                if (appSelected) OK else sw(0x6A82)
            }
            cla == 0x00 && ins == 0xA4 -> {
                if (!appSelected) return sw(0x6A82)
                selectedFile = ((command[5].toInt() and 0xFF) shl 8) or (command[6].toInt() and 0xFF)
                OK
            }
            cla == 0x00 && ins == 0x20 -> {
                if (command[4].toInt() == 0) return if (unlocked) OK else sw(0x6300)
                if (command.copyOfRange(5, 21).contentEquals(password)) { unlocked = true; OK } else sw(0x63C2)
            }
            cla == 0x00 && ins == 0xB0 -> read(command)
            cla == 0x00 && ins == 0xD6 -> write(command)
            cla == 0xA2 && ins == 0xD6 -> {
                appSelected = false
                selectedFile = null
                if ((command[3].toInt() and 0xFF) != 0x1F) return sw(0x6A86)
                val high = command[5].toInt() == 1
                if (!gpoHigh && high) onRisingEdge()
                gpoHigh = high
                OK
            }
            else -> sw(0x6D00)
        }
    }

    private fun read(command: ByteArray): ByteArray {
        val file = when (selectedFile) {
            0x0001 -> statusFile
            0x0002 -> commandFile
            else -> return sw(0x6986)
        }
        val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
        val length = command[4].toInt() and 0xFF
        if (offset + length > file.size) return sw(0x6A82)
        return file.copyOfRange(offset, offset + length) + OK
    }

    private fun write(command: ByteArray): ByteArray {
        if (selectedFile != 0x0002) return sw(0x6982)
        if (!unlocked) return sw(0x6982)
        val offset = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
        val data = command.copyOfRange(5, 5 + (command[4].toInt() and 0xFF))
        if (offset == 2 && refuseSelector != null && data.size > 8 && data[6].toInt() == refuseSelector) {
            return sw(0x6A80)
        }
        data.copyInto(commandFile, offset)
        return OK
    }

    private fun onRisingEdge() {
        if (!mcuAnswers) return
        val length = ((commandFile[0].toInt() and 0xFF) shl 8) or (commandFile[1].toInt() and 0xFF)
        if (length < 6 || commandFile[2 + 4].toInt() and 0xFF != 0x80) return
        commandFile[2 + 4] = 0x00
        val crc = Crc16.compute(commandFile, 2 + 4, 2 + length - 2)
        commandFile[2 + length - 2] = (crc ushr 8).toByte()
        commandFile[2 + length - 1] = crc.toByte()
        acknowledged = true
    }

    private companion object {
        val OK = byteArrayOf(0x90.toByte(), 0x00)
        fun sw(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
    }
}
