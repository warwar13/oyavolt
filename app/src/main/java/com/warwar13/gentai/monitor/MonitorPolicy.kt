package com.warwar13.gentai.monitor

import com.warwar13.gentai.protocol.ReadCommand

object MonitorPolicy {
    const val POLL_INTERVAL_MS = 5_000L
    const val RESPONSE_TIMEOUT_MS = 3_000L
    const val MAX_RECONNECT_DELAY_MS = 30_000L

    fun commandsForPoll(pollNumber: Long): List<ReadCommand> = buildList {
        add(ReadCommand.BATTERY_INFO)
        add(ReadCommand.RUNNING_STATUS)
        if (pollNumber % 3L == 0L) add(ReadCommand.CELL_VOLTAGES)
    }

    fun reconnectDelayMs(failures: Int): Long =
        (1_000L shl failures.coerceIn(0, 5)).coerceAtMost(MAX_RECONNECT_DELAY_MS)
}
