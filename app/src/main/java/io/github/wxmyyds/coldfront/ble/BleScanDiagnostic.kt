package io.github.wxmyyds.coldfront.ble

import io.github.wxmyyds.coldfront.domain.CoolerDeviceType

/**
 * 一次 BLE 扫描结果的原始诊断数据(不做任何识别过滤)。
 * 用于「显示全部设备」诊断模式:排查识别失败、查看散热器真实广播内容。
 *
 * @param msd 厂商数据:公司 ID → payload 十六进制
 * @param serviceData 服务数据:UUID → payload 十六进制
 * @param coolerType 若 MSD 匹配到已知散热器型号则非空(卡片高亮,可直接连接)
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

/** 十六进制工具 */
internal fun ByteArray.toHex(): String =
    joinToString(" ") { "%02X".format(it) }
