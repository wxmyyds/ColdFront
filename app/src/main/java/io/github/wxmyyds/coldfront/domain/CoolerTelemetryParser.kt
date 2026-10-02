package io.github.wxmyyds.coldfront.domain

/** Pure decoding shared by BLE reads/notifications. Temperature heuristics are deliberately
 * unchanged: documentation disagrees about calibration and tagged formats; hardware must decide.
 */
internal object CoolerTelemetryParser {
    fun unsignedByte(data: ByteArray): Int? = data.firstOrNull()?.let { it.toInt() and 0xFF }

    fun fanPercent(data: ByteArray, type: CoolerDeviceType): Int? =
        unsignedByte(data)?.let { CoolerBleConstants.rawToPercentage(it, type) }

    fun bigEndianShort(data: ByteArray): Int? {
        if (data.size < 2) return null
        return ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
    }

    fun temperature(data: ByteArray): Float? =
        temperatureRaw(data)?.minus(CoolerBleConstants.TEMPERATURE_OFFSET)

    private fun temperatureRaw(data: ByteArray): Float? {
        if (data.isEmpty()) return null
        if (data.size == 1) return data[0].toFloat().takeIf { it in -40f..80f }
        val b0 = data[0].toInt() and 0xFF
        if (b0 == 0x04) {
            return data.getOrNull(1)?.toInt()?.toFloat()?.takeIf { it in -40f..80f }
        }
        if (data.size >= 2) {
            val be16 = bigEndianShort(data)?.let { raw16 ->
                when {
                    raw16 in 233..353 -> (raw16 - 273).toFloat()
                    raw16 in 40..80 -> raw16.toFloat()
                    else -> null
                }
            }
            if (be16 != null) return be16
            val signed = ((data[0].toInt() shl 8) or (data[1].toInt() and 0xFF)).toShort().toInt()
            if (signed in -40..80) return signed.toFloat()
        }
        return data[0].toInt().toFloat().takeIf { it in -40f..80f }
    }
}
