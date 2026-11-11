package dev.nocny.lessive.protocol

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Crc16Test {
    @Test fun `status record vector from a real tag`() {
        assertEquals(0x9C40, Crc16.compute("3100832519036063243025033".encodeToByteArray()))
    }
}

class StartCycleTest {
    @Test fun `reproduces the command validated on a real machine`() {
        val record = StartCycle(selector = 6, temperature = 2, spin = 1).toRecord()
        assertEquals("D4 01 0E 02 80 0B 06 02 01 00 00 00 00 00 FF 00 89 8B", record.toHex())
    }

    @Test fun `delay is a half-hour count, no delay is FF`() {
        assertEquals(0xFF, StartCycle(1, 0, 0).toRecord()[14].toInt() and 0xFF)
        assertEquals(5, StartCycle(1, 0, 0, delayHalfHours = 5).toRecord()[14].toInt())
    }

    @Test fun `selector 0 is OFF and refused locally`() {
        assertFailsWith<IllegalArgumentException> { StartCycle(0, 0, 0) }
    }
}

class SessionTest {
    private val fast = M24srSession.Timings(settleMs = 1, gpoLowMs = 1, pollIntervalMs = 10, ackTimeoutMs = 200)

    @Test fun `full start sequence is acknowledged`() = runTest {
        val tag = FakeM24sr()
        val steps = mutableListOf<M24srSession.Step>()
        val ack = M24srSession(tag, fast).startCycle(StartCycle(6, 2, 1)) { steps += it }

        assertTrue(tag.acknowledged)
        assertEquals(0x00, ack[4].toInt())
        assertEquals(0x0B, ack[5].toInt())
        assertEquals(M24srSession.Step.entries.toList(), steps.toList())
        // The MCU's rewritten CRC must still be valid.
        val crc = Crc16.compute(ack, 4, 16)
        assertEquals(crc, ((ack[16].toInt() and 0xFF) shl 8) or (ack[17].toInt() and 0xFF))
    }

    @Test fun `every SELECT after the GPO command is preceded by SELECT app`() = runTest {
        val tag = FakeM24sr()
        M24srSession(tag, fast).startCycle(StartCycle(1, 3, 2))
        val firstGpo = tag.log.indexOfFirst { it[0] == 0xA2.toByte() }
        tag.log.drop(firstGpo).zipWithNext().forEach { (prev, next) ->
            val nextIsSelectFile = next[1] == 0xA4.toByte() && next[2] == 0x00.toByte()
            if (nextIsSelectFile) assertTrue(prev[1] == 0xA4.toByte() && prev[2] == 0x04.toByte())
        }
    }

    @Test fun `wrong password reports the retries left`() = runTest {
        val tag = FakeM24sr(password = ByteArray(16) { 0x11 })
        val e = assertFailsWith<WasherException.Locked> { M24srSession(tag, fast).startCycle(StartCycle(1, 0, 0)) }
        assertEquals(2, e.retriesLeft)
    }

    @Test fun `silent machine ends in NoAcknowledgement`() = runTest {
        val tag = FakeM24sr(mcuAnswers = false)
        assertFailsWith<WasherException.NoAcknowledgement> { M24srSession(tag, fast).startCycle(StartCycle(1, 0, 0)) }
    }

    @Test fun `parameter refusal is reported as Refused`() = runTest {
        val tag = FakeM24sr(refuseSelector = 9)
        val e = assertFailsWith<WasherException.Refused> { M24srSession(tag, fast).startCycle(StartCycle(9, 0, 0)) }
        assertEquals(0x6A80, e.sw)
    }

    @Test fun `losing the tag after commit is distinguished from before`() = runTest {
        // 8 commands = up to and including the NLEN commit.
        assertFailsWith<WasherException.LostAfterCommit> {
            M24srSession(FakeM24sr(dropAfterCommands = 8), fast).startCycle(StartCycle(1, 0, 0))
        }
        assertFailsWith<java.io.IOException> {
            M24srSession(FakeM24sr(dropAfterCommands = 3), fast).startCycle(StartCycle(1, 0, 0))
        }
    }
}

class StatusFileTest {
    private fun statusMessage(): ByteArray {
        val uri = byteArrayOf(0x01) + "candysmarttouch.com/vsa/wd/".encodeToByteArray()
        val digits = "3100832519036063243025033".encodeToByteArray()
        val crc = Crc16.compute(digits)
        val status = digits + byteArrayOf((crc ushr 8).toByte(), crc.toByte())
        val r1 = byteArrayOf(0x91.toByte(), 1, uri.size.toByte(), 'U'.code.toByte()) + uri
        val r2 = byteArrayOf(0x54, 1, status.size.toByte(), '1'.code.toByte()) + status
        return r1 + r2
    }

    @Test fun `parses the appliance code and checks the CRC`() {
        val status = StatusFile.parse(statusMessage())
        assertEquals("http://www.candysmarttouch.com/vsa/wd/", status.uri)
        assertEquals("3100832519036063", status.applianceCode)
        assertEquals("243025033", status.extraDigits)
        assertTrue(status.crcValid)
    }

    @Test fun `reads the status file through the session`() = runTest {
        val message = statusMessage()
        val file = byteArrayOf((message.size ushr 8).toByte(), message.size.toByte()) + message
        val status = M24srSession(FakeM24sr(statusFile = file)).readStatus()
        assertEquals("3100832519036063", status.applianceCode)
    }

    @Test fun `garbage does not crash`() {
        assertNull(StatusFile.parse(byteArrayOf(0x12, 0x34)).digits)
    }
}
