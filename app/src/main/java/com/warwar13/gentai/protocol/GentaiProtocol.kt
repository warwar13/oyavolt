package com.warwar13.gentai.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object GentaiBle {
    val namePrefixes = listOf("DCHE", "BMC1_")
    const val serviceUuid = "00000001-0000-1000-8000-00805f9b34fb"
    const val writeCharacteristicUuid = "00000002-0000-1000-8000-00805f9b34fb"
    const val notifyCharacteristicUuid = "00000003-0000-1000-8000-00805f9b34fb"
}

enum class ReadCommand(val value: Int) {
    HANDSHAKE(0x00), MANUFACTURER(0x10), PACK_NAME(0x11), RUNNING_STATUS(0x20),
    BATTERY_INFO(0x21), CELL_VOLTAGES(0x22), FIRMWARE(0xf5);

    companion object {
        fun fromValue(value: Int): ReadCommand? = entries.firstOrNull { it.value == value }
    }
}

/** The sole packet-construction API. It cannot construct BMS control packets. */
object ReadOnlyGentaiProtocol {
    fun buildReadQuery(command: Int): ByteArray {
        require(ReadCommand.fromValue(command) != null) {
            "Blocked non-read BMS command: 0x${command.toString(16)}"
        }
        return byteArrayOf(0xaa.toByte(), command.toByte(), 0, command.toByte(), 0)
    }
}

data class GentaiFrame(val command: Int, val payload: ByteArray, val raw: ByteArray)

fun checksum16(bytes: ByteArray): Int = bytes.fold(0) { sum, byte -> (sum + byte.unsigned()) and 0xffff }

fun parseFrame(bytes: ByteArray): GentaiFrame {
    require(bytes.size >= 5) { "Truncated BMS frame" }
    require(bytes[0].unsigned() == 0xaa) { "Invalid BMS frame marker" }
    val payloadLength = bytes[2].unsigned()
    val expectedLength = payloadLength + 5
    require(bytes.size == expectedLength) { "Invalid BMS frame length: expected $expectedLength, received ${bytes.size}" }
    val payloadEnd = 3 + payloadLength
    val actualChecksum = bytes[payloadEnd].unsigned() or (bytes[payloadEnd + 1].unsigned() shl 8)
    require(actualChecksum == checksum16(bytes.copyOfRange(1, payloadEnd))) { "Invalid BMS frame checksum" }
    return GentaiFrame(bytes[1].unsigned(), bytes.copyOfRange(3, payloadEnd), bytes.copyOf())
}

class FrameStream(
    private val maxFrameSize: Int = 512,
    private val onError: (IllegalArgumentException) -> Unit = {},
) {
    private val buffer = mutableListOf<Byte>()

    fun reset() = buffer.clear()

    fun push(chunk: ByteArray): List<GentaiFrame> {
        buffer += chunk.toList()
        val frames = mutableListOf<GentaiFrame>()
        while (buffer.isNotEmpty()) {
            val marker = buffer.indexOfFirst { it.unsigned() == 0xaa }
            if (marker == -1) {
                onError(IllegalArgumentException("Discarded notification without a BMS frame marker"))
                buffer.clear()
                break
            }
            if (marker > 0) {
                onError(IllegalArgumentException("Discarded $marker byte(s) before a BMS frame"))
                repeat(marker) { buffer.removeAt(0) }
            }
            if (buffer.size < 3) break
            val expectedLength = buffer[2].unsigned() + 5
            if (expectedLength > maxFrameSize) {
                onError(IllegalArgumentException("Rejected oversized BMS frame ($expectedLength bytes)"))
                buffer.removeAt(0)
                continue
            }
            if (buffer.size < expectedLength) break
            val candidate = ByteArray(expectedLength) { buffer.removeAt(0) }
            try {
                frames += parseFrame(candidate)
            } catch (error: IllegalArgumentException) {
                onError(error)
            }
        }
        return frames
    }
}

data class Temperatures(val probes: List<Int>, val ambient: Int, val mos: Int)
data class BatteryInfo(
    val voltageV: Double, val currentA: Double, val socPct: Int, val sohPct: Int,
    val remainingAh: Double, val fullAh: Double, val cycles: Int, val temperaturesC: Temperatures,
)

fun decodeIdentity(payload: ByteArray): String = payload.decodeToString().replace("\u0000", "").trim()

fun decodeBatteryInfo(payload: ByteArray): BatteryInfo {
    require(payload.size >= 26) { "Battery-info response is too short" }
    val view = payload.littleEndianBuffer()
    val info = BatteryInfo(
        voltageV = view.int / 1000.0, currentA = view.int / 1000.0,
        socPct = view.get().unsigned(), sohPct = view.get().unsigned(),
        remainingAh = view.int / 1000.0, fullAh = view.int / 1000.0, cycles = view.short.unsigned(),
        temperaturesC = Temperatures((20..23).map { payload[it].toInt() }, payload[24].toInt(), payload[25].toInt()),
    )
    require(isPlausibleBatteryInfo(info)) { "Battery-info values are outside safe validation limits" }
    return info
}

data class RunningStatus(
    val bitfield: Long, val warnings: List<String>, val chargeFetOn: Boolean,
    val dischargeFetOn: Boolean, val heating: Boolean, val balanceMask: Long, val disconnectedCellMask: Int,
)

private val statusFlags = listOf(
    "Charge overcurrent protection", "Charge over-temperature protection", "Charge under-temperature protection", "Cell overvoltage protection", "Pack overvoltage protection", "AFE error", "Charging stopped", "Charge FET on", "Charge overcurrent warning", "Charge over-temperature warning", "Charge under-temperature warning", "Cell overvoltage warning", "Pack overvoltage warning", "Cell voltage-difference warning", "Cell voltage difference too large", "Heating active", "Discharge overcurrent protection", "Discharge over-temperature protection", "Discharge under-temperature protection", "Cell undervoltage protection", "Short-circuit protection", "Pack undervoltage protection", "Discharging stopped", "Discharge FET on", "Discharge overcurrent warning", "Discharge over-temperature warning", "Discharge under-temperature warning", "Cell undervoltage warning", "Pack undervoltage warning", "MOS over-temperature warning", "MOS over-temperature protection", "Pre-discharge FET on",
)

fun decodeRunningStatus(payload: ByteArray): RunningStatus {
    require(payload.size >= 15) { "Running-status response is too short" }
    val view = payload.littleEndianBuffer()
    view.position(4)
    val bitfield = view.int.toLong() and 0xffffffffL
    val warnings = statusFlags.filterIndexed { bit, _ -> bit !in setOf(7, 15, 23, 31) && bitfield and (1L shl bit) != 0L }
    view.position(8)
    val balanceMask = view.int.toLong() and 0xffffffffL
    return RunningStatus(bitfield, warnings, bitfield and 0x80 != 0L, bitfield and 0x00800000 != 0L, bitfield and 0x8000 != 0L, balanceMask, payload[12].unsigned() or (payload[13].unsigned() shl 8) or (payload[14].unsigned() shl 16))
}

fun decodeCellVoltages(payload: ByteArray): List<Double> {
    require(payload.size >= 2 && payload.size % 2 == 0) { "Invalid cell-voltage response length" }
    val view = payload.littleEndianBuffer()
    val cells = buildList { while (view.remaining() >= 2) view.short.unsigned().takeIf { it != 0 }?.let { add(it / 1000.0) } }
    require(cells.isNotEmpty() && cells.all { it in 1.5..5.0 }) { "Cell voltages are outside safe validation limits" }
    return cells
}

fun isPlausibleBatteryInfo(info: BatteryInfo): Boolean =
    info.voltageV in 8.0..16.5 && abs(info.currentA) <= 500 && info.socPct in 0..100 && info.sohPct in 0..100 && info.remainingAh in 0.0..500.0 && info.fullAh in 0.000001..500.0

private fun Byte.unsigned(): Int = toInt() and 0xff
private fun Short.unsigned(): Int = toInt() and 0xffff
private fun ByteArray.littleEndianBuffer(): ByteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
