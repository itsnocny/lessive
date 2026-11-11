package dev.nocny.lessive.protocol

import kotlinx.coroutines.delay
import java.io.IOException

/** Sends one command APDU and returns the raw response (data + SW1 SW2). */
fun interface ApduTransport {
    @Throws(IOException::class)
    fun transceive(command: ByteArray): ByteArray
}

/**
 * One RF session with the washer's ST M24SR tag.
 *
 * The whole write → GPO pulse → acknowledgement exchange must happen on a single open
 * session: the MCU only reads the command file after a low→high edge on the GPO pin,
 * and dropping the field in between never produces an acknowledgement.
 */
class M24srSession(
    private val transport: ApduTransport,
    private val timings: Timings = Timings(),
    private val password: ByteArray = ByteArray(16),
) {
    data class Timings(
        val settleMs: Long = 200,
        val gpoLowMs: Long = 1200,
        val pollIntervalMs: Long = 350,
        val ackTimeoutMs: Long = 8000,
    )

    enum class Step { Connecting, Unlocking, Writing, Signalling, WaitingForMachine }

    /** Reads the free status file. Never writes anything. */
    suspend fun readStatus(): StatusFile {
        expectOk("SELECT NDEF", Apdu.SELECT_NDEF_APP)
        return StatusFile.parse(readNdefFile(Apdu.FILE_STATUS))
    }

    /** Reads the command file as it is now (last command or last response). */
    fun readCommandFile(): ByteArray {
        expectOk("SELECT NDEF", Apdu.SELECT_NDEF_APP)
        return readNdefFile(Apdu.FILE_COMMAND)
    }

    /** Starts a wash cycle: see [sendCommand]. */
    suspend fun startCycle(command: StartCycle, onStep: (Step) -> Unit = {}): ByteArray =
        sendCommand(command.toRecord(), onStep)

    /** Sends a read-only request and returns its answer bytes. Nothing starts on the machine. */
    suspend fun request(request: ReadRequest, onStep: (Step) -> Unit = {}): ByteArray =
        request.answer(sendCommand(request.toRecord(), onStep))

    /**
     * Writes a command record, signals the MCU and waits for it to flip the marker byte
     * 0x80 → 0x00. Returns the record the machine wrote back.
     */
    suspend fun sendCommand(record: ByteArray, onStep: (Step) -> Unit = {}): ByteArray {

        onStep(Step.Connecting)
        expectOk("SELECT NDEF", Apdu.SELECT_NDEF_APP)
        expectOk("SELECT 0002", Apdu.selectFile(Apdu.FILE_COMMAND))

        onStep(Step.Unlocking)
        unlock()

        onStep(Step.Writing)
        expectOk("NLEN = 0", Apdu.updateBinary(0, bytes(0, 0)))
        val write = send(Apdu.updateBinary(2, record))
        if (!write.ok) throw refusalOrUnexpected("WRITE", write.sw)
        expectOk("NLEN commit", Apdu.updateBinary(0, bytes(record.size ushr 8, record.size and 0xFF)))

        // From here the command is on the tag: losing the field no longer means "nothing happened".
        try {
            onStep(Step.Signalling)
            delay(timings.settleMs)
            pulseGpo(high = false)
            delay(timings.gpoLowMs)
            pulseGpo(high = true)

            onStep(Step.WaitingForMachine)
            return awaitAcknowledgement(record[5])
        } catch (e: IOException) {
            throw WasherException.LostAfterCommit(e)
        }
    }

    private fun unlock() {
        val probe = send(Apdu.VERIFY_PROBE)
        if (probe.ok) return
        val verify = send(Apdu.verifyWritePassword(password))
        if (verify.ok) return
        val retries = if (verify.sw and 0xFFF0 == 0x63C0) verify.sw and 0x0F else null
        throw WasherException.Locked(retries)
    }

    /** The A2 command drops the ISO application context, so re-select it first every time. */
    private fun pulseGpo(high: Boolean) {
        send(Apdu.SELECT_NDEF_APP)
        send(Apdu.selectFile(Apdu.FILE_SYSTEM))
        send(Apdu.gpo(high))
    }

    private suspend fun awaitAcknowledgement(action: Byte): ByteArray {
        val deadline = timings.ackTimeoutMs / timings.pollIntervalMs + 1
        repeat(deadline.toInt()) {
            val content = runCatching { readCommandFile() }.getOrElse { e ->
                if (e is IOException) throw e else null
            }
            if (content != null && content.size > 5 &&
                content[4].toInt() and 0xFF == 0x00 && content[5] == action
            ) return content
            delay(timings.pollIntervalMs)
        }
        throw WasherException.NoAcknowledgement()
    }

    private fun readNdefFile(fileId: Int): ByteArray {
        expectOk("SELECT %04X".format(fileId), Apdu.selectFile(fileId))
        val header = expectOk("READ NLEN", Apdu.readBinary(0, 2))
        require(header.data.size >= 2) { "short NLEN" }
        val length = ((header.data[0].toInt() and 0xFF) shl 8) or (header.data[1].toInt() and 0xFF)
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val chunk = minOf(length - offset, MAX_READ)
            val part = expectOk("READ", Apdu.readBinary(2 + offset, chunk)).data
            if (part.isEmpty()) break
            part.copyInto(out, offset, 0, minOf(part.size, length - offset))
            offset += part.size
        }
        return out
    }

    private fun send(command: ByteArray) = Response(transport.transceive(command))

    private fun expectOk(step: String, command: ByteArray): Response {
        val response = send(command)
        if (!response.ok) throw WasherException.UnexpectedStatus(step, response.sw)
        return response
    }

    private fun refusalOrUnexpected(step: String, sw: Int): WasherException = when (sw) {
        0x6A80, 0x6B00, 0x6700, 0x6A86 -> WasherException.Refused(sw)
        else -> WasherException.UnexpectedStatus(step, sw)
    }

    private companion object {
        const val MAX_READ = 0xF6
    }
}
