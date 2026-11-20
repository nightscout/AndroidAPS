package app.aaps.plugins.eversense.protocol

/** Calibration wire layouts keep sample time separate from the submission clock. */
internal object CalibrationPayload {
    fun e3(glucose: Int, sampledAt: Long, submittedAt: Long): ByteArray =
        BinaryFields.date(sampledAt) + BinaryFields.time(sampledAt) +
            BinaryFields.date(submittedAt) + BinaryFields.time(submittedAt) +
            BinaryFields.littleEndian(glucose.toLong(), 2) + byteArrayOf(0, 0, 0, 0x55)

    fun v2(glucose: Int, sampledAt: Long, submittedAt: Long): ByteArray =
        BinaryFields.sensorTimestamp(sampledAt) + BinaryFields.sensorTimestamp(submittedAt) +
            BinaryFields.littleEndian(glucose.toLong(), 2) + byteArrayOf(1, 0, 0)
}
