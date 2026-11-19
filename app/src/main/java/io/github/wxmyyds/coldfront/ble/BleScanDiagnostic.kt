package io.github.wxmyyds.coldfront.ble

import io.github.wxmyyds.coldfront.domain.CoolerDeviceType

/**
 * 一次 BLE 扫描结果的原始诊断数据，不经过设备识别筛选。
 * 用于“显示全部设备”模式，排查未识别设备并查看其广播内容。
 *
 * @param msd 厂商 ID 到十六进制 payload 的映射
 * @param serviceData 服务 UUID 到十六进制 payload 的映射
 * @param coolerType MSD 匹配已知型号时非空，诊断列表会高亮该设备并允许直接连接
 */
data class BleScanDiagnostic(
    val address: String,
    val name: String?,
    val rssi: Int,
    val msd: List<Pair<Int, String>>,
    val serviceUuids: List<String>,
    val serviceData: List<Pair<String, String>>,
    val coolerType: CoolerDeviceType? = null,
)

/** 将字节数组转为以空格分隔的大写十六进制字符串。 */
internal fun ByteArray.toHex(): String =
    joinToString(" ") { "%02X".format(it) }
