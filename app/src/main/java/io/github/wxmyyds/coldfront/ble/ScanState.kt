package io.github.wxmyyds.coldfront.ble

data class ScanState(
    val scanning: Boolean = false,
    val permissionGranted: Boolean = false,
    val locationServiceEnabled: Boolean = true,
    val bluetoothOn: Boolean = false,
    val errorCode: Int? = null,
)

internal fun ScanState.failed(errorCode: Int): ScanState = copy(
    scanning = false,
    errorCode = errorCode,
)
