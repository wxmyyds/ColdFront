package io.github.wxmyyds.coldfront.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScanStateTest {
    @Test
    fun `scan failure stops scanning and preserves current prerequisites`() {
        val state = ScanState(
            scanning = true,
            permissionGranted = true,
            locationServiceEnabled = false,
            bluetoothOn = true,
        )

        val failed = state.failed(errorCode = 3)

        assertFalse(failed.scanning)
        assertEquals(3, failed.errorCode)
        assertEquals(true, failed.permissionGranted)
        assertEquals(false, failed.locationServiceEnabled)
        assertEquals(true, failed.bluetoothOn)
    }
}
