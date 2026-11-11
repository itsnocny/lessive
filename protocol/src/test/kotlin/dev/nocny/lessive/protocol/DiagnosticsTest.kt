package dev.nocny.lessive.protocol

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticsTest {
    @Test fun `counters request matches the record read from an EY 12102DE tag`() {
        val expected = "D4 01 2E 02 80 01 " + List(42) { "00" }.joinToString(" ") + " AE 18"
        assertEquals(expected, ReadRequest.ProgramCycleCounters.toRecord().toHex())
    }

    @Test fun `counters are big-endian and stored minus one`() {
        val answer = hexToBytes("FFFF 0000 0029")
        assertEquals(listOf(0, 1, 42), Answers.programCounters(answer))
    }

    @Test fun `software version is three numbers and two letters`() {
        assertEquals("123AB", Answers.softwareVersion(byteArrayOf(1, 2, 3, 'A'.code.toByte(), 'B'.code.toByte())))
    }

    @Test fun `read request goes through the full handshake`() = runTest {
        val tag = FakeM24sr()
        val answer = M24srSession(tag, M24srSession.Timings(1, 1, 10, 200)).request(ReadRequest.ProgramCycleCounters)
        assertTrue(tag.acknowledged)
        assertEquals(42, answer.size)
    }
}
