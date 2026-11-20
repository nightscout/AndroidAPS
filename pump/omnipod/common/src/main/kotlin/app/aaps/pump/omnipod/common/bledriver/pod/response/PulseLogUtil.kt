package app.aaps.pump.omnipod.common.bledriver.pod.response

import java.nio.ByteBuffer

/** Parses a trailing run of big-endian 32-bit pulse-log entries, shared by the
 *  status-page responses that carry one (types 3/80/81) - each entry is opaque,
 *  bit-packed telemetry, not a plain counter (see OmnipodKit's PodInfoPulseLog.swift
 *  `binaryDescription` debug helper), so no further interpretation happens here. */
internal fun parsePulseLog(encoded: ByteArray, startOffset: Int, entryCount: Int): List<Int> =
    (0 until entryCount).map { i ->
        val base = startOffset + i * 4
        ByteBuffer.wrap(byteArrayOf(encoded[base], encoded[base + 1], encoded[base + 2], encoded[base + 3])).int
    }
