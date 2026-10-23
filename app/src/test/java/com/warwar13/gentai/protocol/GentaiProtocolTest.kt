package com.warwar13.gentai.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GentaiProtocolTest {
    @Test fun `only documented telemetry queries can be built`() {
        assertArrayEquals(byteArrayOf(0xaa.toByte(), 0x21, 0, 0x21, 0), ReadOnlyGentaiProtocol.buildReadQuery(ReadCommand.BATTERY_INFO.value))
        assertEquals(listOf(0x00, 0x10, 0x11, 0x20, 0x21, 0x22, 0xF5), ReadCommand.entries.map { it.value })
        ReadCommand.entries.forEach { command ->
            assertEquals(command.value, ReadOnlyGentaiProtocol.buildReadQuery(command.value)[1].toInt() and 0xff)
        }
        listOf(-1, 0x01, 0x52, 0x53, 0x100).forEach { command ->
            try { ReadOnlyGentaiProtocol.buildReadQuery(command); throw AssertionError("command was allowed") }
            catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("Blocked non-read")) }
        }
    }

    @Test fun `validates response checksums`() {
        val frame = responseFrame(0x10, "Gentai".encodeToByteArray())
        assertEquals(0x10, parseFrame(frame).command)
        frame[3] = (frame[3].toInt() xor 1).toByte()
        expectIllegalArgument { parseFrame(frame) }
    }

    @Test fun `reassembles fragmented frames and resynchronizes`() {
        val frame = responseFrame(0x22, ByteArray(48) { it.toByte() })
        val stream = FrameStream()
        assertTrue(stream.push(byteArrayOf(1, 2) + frame.copyOfRange(0, 7)).isEmpty())
        assertTrue(stream.push(frame.copyOfRange(7, 20)).isEmpty())
        val completed = stream.push(frame.copyOfRange(20, frame.size))
        assertEquals(1, completed.size)
        assertEquals(48, completed.single().payload.size)
    }

    @Test fun `emits combined notification frames`() {
        val stream = FrameStream()
        val combined = responseFrame(0x10, byteArrayOf(1)) + responseFrame(0x11, byteArrayOf(2, 3))
        assertEquals(listOf(0x10, 0x11), stream.push(combined).map { it.command })
    }

    @Test fun `decodes signed discharge current and core info`() {
        val payload = ByteArray(26)
        val view = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        view.putInt(0, 13_247); view.putInt(4, -12_500); payload[8] = 64; payload[9] = 98
        view.putInt(10, 64_000); view.putInt(14, 100_000); view.putShort(18, 42)
        val info = decodeBatteryInfo(payload)
        assertEquals(13.247, info.voltageV, 0.0); assertEquals(-12.5, info.currentA, 0.0)
        assertEquals(64.0, info.remainingAh, 0.0); assertEquals(42, info.cycles)
    }

    @Test fun `decodes cells and status warnings`() {
        val cells = ByteArray(48); val view = ByteBuffer.wrap(cells).order(ByteOrder.LITTLE_ENDIAN)
        listOf(3310, 3308, 3312, 3309).forEachIndexed { index, value -> view.putShort(index * 2, value.toShort()) }
        assertEquals(listOf(3.31, 3.308, 3.312, 3.309), decodeCellVoltages(cells))
        val status = ByteArray(15); ByteBuffer.wrap(status).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 0x81)
        val decoded = decodeRunningStatus(status)
        assertTrue(decoded.chargeFetOn); assertEquals(listOf("Charge overcurrent protection"), decoded.warnings)
    }

    private fun responseFrame(command: Int, payload: ByteArray): ByteArray {
        val body = byteArrayOf(command.toByte(), payload.size.toByte()) + payload
        val checksum = checksum16(body)
        return byteArrayOf(0xaa.toByte()) + body + byteArrayOf(checksum.toByte(), (checksum shr 8).toByte())
    }

    private fun expectIllegalArgument(block: () -> Unit) { try { block(); throw AssertionError("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) {} }
}
