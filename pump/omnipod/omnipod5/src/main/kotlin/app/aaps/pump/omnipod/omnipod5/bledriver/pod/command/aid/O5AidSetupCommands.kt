package app.aaps.pump.omnipod.omnipod5.bledriver.pod.command.aid

import app.aaps.pump.omnipod.common.bledriver.comm.session.Session
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * O5-only "AID setup" command batch - ported from OmnipodKit's O5AidCommands.swift /
 * BlePodComms.o5SendAidSetupCommands() (loopandlearn/OmnipodKit). Required right after
 * pairing/session establishment and before anything else in activation - the pod's
 * onboard AID firmware expects this data even though AAPS runs O5 as a manual pod with
 * that firmware's own automated-delivery mode never engaged (see
 * [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin]'s class doc). Skipping or getting any of
 * these wrong blocks activation from completing: the reference throws immediately on any
 * failure here and never proceeds to SetupPod/SetUniqueId, and [send] mirrors that by
 * propagating [Session.sendAidSetupCommand]'s exceptions rather than swallowing them.
 *
 * Every value below except the UTC timestamp is a fixed placeholder the pod firmware
 * never actually acts on for dosing (AAPS is the sole dosing decision-maker) - these
 * exact fixed values, and every wire-format byte layout here, are cross-verified against
 * a real captured O5 pairing's comm log (OmnipodKit's own OmniTests/O5/
 * O5CommLogFixtures.swift + O5AidCommandsTests.swift), not guessed from protocol
 * documentation.
 *
 * The 9th AID command upstream (a pod-status query, feature `3.11`/`3.12`) is
 * unconditionally skipped in OmnipodKit itself (`skipO5AID9 = true`, "isn't even
 * needed") and isn't ported here for the same reason.
 */
object O5AidSetupCommands {

    fun send(session: Session) {
        session.sendAidSetupCommand(utcCommand(), "ES255.2=")
        session.sendAidSetupCommand(tdiCommand(), "3.2=")
        session.sendAidSetupCommand(targetBgProfileCommand(), "3.1=")
        session.sendAidSetupCommand(diaCommand(), "3.9=")
        session.sendAidSetupCommand(egvCommand(), "3.7=")
        repeat(INSULIN_HISTORY_BATCH_COUNT) {
            session.sendAidSetupCommand(insulinHistoryCommand(), "ES2.1=")
        }
    }

    /** Feature 255.2, Extended SET - the only AID command carrying a real (non-fixed)
     *  value: the controller's current wall-clock time. */
    private fun utcCommand(): ByteArray {
        val utcSeconds = System.currentTimeMillis() / 1000
        return "SE255.2=$utcSeconds".toByteArray(StandardCharsets.US_ASCII)
    }

    /** Feature 3.2 (therapy delivery info / TDI), SET+GET - fixed 5-byte body:
     *  version(0x00), therapy-type(0x03), delivery-mode(0x00), bolus-speed(0x0E),
     *  reserved(0x00). Never varies with AAPS's actual profile. */
    private fun tdiCommand(): ByteArray {
        val data = byteArrayOf(0x00, 0x03, 0x00, 0x0E, 0x00)
        return "S3.2=".toByteArray(StandardCharsets.US_ASCII) + data + ",G3.2".toByteArray(StandardCharsets.US_ASCII)
    }

    /** Feature 3.1 (target BG profile), SET+GET - 48 half-hour slots, each a 4-byte
     *  big-endian mg/dL value, all fixed at [DEFAULT_TARGET_MGDL] regardless of AAPS's
     *  real target profile (the pod firmware doesn't use this for dosing). */
    private fun targetBgProfileCommand(): ByteArray {
        val body = ByteBuffer.allocate(2 + TARGET_SLOT_COUNT * 4)
        body.putShort((TARGET_SLOT_COUNT * 4).toShort())
        repeat(TARGET_SLOT_COUNT) { body.putInt(DEFAULT_TARGET_MGDL) }
        return "S3.1=".toByteArray(StandardCharsets.US_ASCII) + body.array() + ",G3.1".toByteArray(StandardCharsets.US_ASCII)
    }

    /** Feature 3.9 (DIA), SET+GET - fixed ASCII value. OmnipodKit's own comment hedges on
     *  the exact unit semantics ("likely 8 half-hours = 4h DIA, but could be the raw
     *  value") - ported as the same fixed value regardless, since AAPS's own DIA setting
     *  is what actually governs dosing, not this pod-firmware field. */
    private fun diaCommand(): ByteArray =
        "S3.9=$DEFAULT_DIA_VALUE,G3.9".toByteArray(StandardCharsets.US_ASCII)

    /** Feature 3.7 (EGV/CGM config), SET+GET - fixed ASCII composite/bitfield value;
     *  OmnipodKit doesn't decode its bit meaning either, it's just replicated verbatim. */
    private fun egvCommand(): ByteArray =
        "S3.7=$DEFAULT_EGV_CONFIG,G3.7".toByteArray(StandardCharsets.US_ASCII)

    /** Feature 2.1 (algorithm insulin history), Extended SET - 24 all-zero 7-byte
     *  records (no real delivery history to report; AAPS is the dosing decision-maker,
     *  not the pod). */
    private fun insulinHistoryCommand(): ByteArray {
        val body = ByteArray(INSULIN_HISTORY_RECORD_COUNT * INSULIN_HISTORY_RECORD_SIZE)
        val header = ByteBuffer.allocate(2).putShort(body.size.toShort()).array()
        return "SE2.1=".toByteArray(StandardCharsets.US_ASCII) + header + body
    }

    private const val TARGET_SLOT_COUNT = 48
    private const val DEFAULT_TARGET_MGDL = 110
    private const val DEFAULT_DIA_VALUE = 8
    private const val DEFAULT_EGV_CONFIG = 3670015
    private const val INSULIN_HISTORY_RECORD_COUNT = 24
    private const val INSULIN_HISTORY_RECORD_SIZE = 7
    private const val INSULIN_HISTORY_BATCH_COUNT = 3
}
