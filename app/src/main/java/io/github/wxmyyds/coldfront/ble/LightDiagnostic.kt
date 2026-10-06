package io.github.wxmyyds.coldfront.ble

import android.bluetooth.BluetoothGattCharacteristic

/**
 * 0x1013 灯效读取的实时诊断快照。
 *
 * 仅用于定位「读不到灯效」问题(read 超时 / 有回包但无法解析 / 特征缺失)。据此可区分三类根因:
 * - [present] = false,或 [propertiesText] 不含 READ → 设备特征表里没有 0x1013 或不可读;
 * - [replies] = 0 → 设备从不回包(read 每次都超时,写 0x11 查询也没有 notify);
 * - [replies] > 0 且 [lastReplyParsed] = false → 有回包,但字节无法解析为有效灯效。
 */
data class LightDiagnostic(
    /** 设备特征表里是否存在 0x1013 */
    val present: Boolean = false,
    /** 0x1013 的 GATT properties 原始位 */
    val properties: Int = 0,
    /** 绑定的 0x1013 所属服务 UUID */
    val serviceUuid: String? = null,
    /** 所有 0x1013 候选("svc=... props=...");多于一条说明设备暴露了重复特征 */
    val candidates: List<String> = emptyList(),
    /** notify(CCCD) 是否订阅成功 */
    val notifySubscribed: Boolean = false,
    /** 已发起的 read 次数 */
    val reads: Int = 0,
    /** 最近一次 read 的结果:"ok" / "timeout" */
    val readResult: String? = null,
    /** 收到的 0x1013 回包(read 或 notify)次数 */
    val replies: Int = 0,
    /** 最近一次回包来源:"read" / "notify" */
    val lastReplySource: String? = null,
    /** 最近一次回包的原始字节(十六进制) */
    val lastReplyHex: String? = null,
    /** 最近一次回包是否被解析为有效灯效 */
    val lastReplyParsed: Boolean? = null,
    /** 最近若干次回包,形如 "read 11 00 00 00"(旧→新) */
    val history: List<String> = emptyList(),
) {
    /** properties 的可读文本,例如 "READ|WRITE|NOTIFY";无已知位时回退为原始十六进制 */
    val propertiesText: String
        get() {
            val p = properties
            val parts = buildList {
                if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
                if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NR")
                if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
                if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
            }
            return if (parts.isEmpty()) "0x%02X".format(p) else parts.joinToString("|")
        }
}
