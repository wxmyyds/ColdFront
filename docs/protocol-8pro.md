# 红魔散热器 8 Pro BLE 接口

> 来源 1:`cn.nubia.externdevice` 官方应用 smali 反编译(`cn/nubia/device/bluetooth/jacket8pro/`)。
> 来源 2:**实测可用的第三方控制 App**(`com.magcooler.cpucontrol`,特征语义带中文日志)。
> 两者冲突处以实测为准,已标注。
>
> **校准待核对**：本文件的实测资料记录“无 −6°C 偏移”，但当前应用沿用官方资料的 −6°C 校准。本轮全局代码审查未获得新的硬件对照数据，因此未改动该行为；不能把此处协议描述视作已完成全型号校准验证。

## 1. 设备识别(广播)

| 项 | 值 |
|---|---|
| 广播 Service UUID | `00004a41-0000-1000-8000-00805f9b34fb`(家族标记,须在 ServiceUuids 列表中) |
| 厂商数据(Manufacturer Specific Data) | Company ID **0x08CA(2250)**,payload 前 2 字节 = `[mainType, subType]` |
| 8 Pro 识别码 | **mainType = 0x05, subType = 0x08** |
| 全系识别码 | 3=(5,3) 4=(5,4) 5/5Lite=(5,5) 6/6Pro=(5,6) 8Pro=(5,8) |
| OTA 模式名 | BLE 名含 `PPlusOTA`(识别放行) |
| Device 枚举 | `JACKET8Pro`,设备代码 `rm_charge_clip8_pro` |

### 实机抓包样例(8 Pro,已验证)

```
名称:   RM Magcooler 8pro
MAC:    D0:00:FF:40:15:ED
RSSI:   -43 dBm
厂商数据 0x08CA:  05 08 02 50 02 00 00 00 00 00 00
服务 UUID:       00004a41-0000-1000-8000-00805f9b34fb
```

即 MSD 前 2 字节 `05 08` 与逆向结论完全一致(第 3 字节 0x02、第 4 字节 0x50 用意未知,
0x50 = 80 恰为风扇 raw 上限,疑为能力/状态字段,待验证)。

## 2. GATT 结构

⚠️ **特征值不依赖特定服务 UUID**——实测 App 遍历所有服务查找特征(8 Pro 服务 UUID 可能与旧的 `d52082ad-...` 不同,不可只 getService(主服务))。

| 特征值 | 语义(实测) | 属性 |
|---|---|---|
| `00001011-...` | **散热总开关:写 0x02 开 / 0x03 关;通知 2=开 3=关。不写 ON 风扇不转!** | R/W/Notify |
| `00001012-...` | **风扇调速:单字节 raw(8 Pro 40–80 共 8 档)** | R/W |
| `00001013-...` | **灯光状态/控制:[mode][R][G][B]**;官方 `queryLight` 通过 GATT 主动读取,不写查询字节 | R/W/Notify |
| `00001014-...` | **背夹温度:单字节有符号 °C;固件 8.4.7 为 [0x04, 温度] 包。无 -6 偏移** | Notify |
| `00001015-...` | **状态包:[tag,...]:tag 0x08 → 后2字节大端 RPM;tag 0x09 → 后1字节功率 W** | Notify |
| `00001016-...` | LOGGER 日志(UTF-8) | R/Notify |
| `00001017-...` | **Boost/破坏神(超频):写 0x01/0x00** | R/W |
| `00001018-...` | **智能温控:写 0x01 开 / 0x00 关;通知 1=开** | R/W/Notify |
| `00001019-...` | 旧版风扇转速 | R |
| `0000101a-...` | 旧版功率 | R |
| `0000101c-...` | **风扇转速(官方 8.4.7):大端 16 位 RPM** | Notify |
| `0000101d-...` | **功率(官方 8.4.7):byte0 = W** | Notify |
| `0000101f-...` | **温度告警/保护配置:**至少 4 字节;byte0 bit0..3 为保护位,byte1 事件 ID,byte2/3 阈值 | R/W |

写类型:`WRITE_TYPE_DEFAULT(2)`。**操作必须串行**(Android BLE 同一时刻仅一个在途操作)。

## 3. 核心命令

### 3.1 风扇调速(0x1012)
- 写**单字节 raw**,8 Pro 范围 **40(0x28)–80(0x50)**,8 档
- 换算:`raw = 40 + percent × 40 / 100`
- UI 档位映射(官方 Companion.d):40–42→0档,43–48→1,49–54→2,55–60→3,61–66→4,67–70→5,71–74→6,≥75→7
- 读取/Notify 回读当前 raw

### 3.2 自动模式(0x1018)
- 开:**写 `0x01`**(官方日志"8PRO 背夹 writeAutoOn 写1")
- 关:**写 `0x00`**("writeAutoOff 写0")

### 3.3 温度(0x1014 Notify)
- 单字节**有符号** °C(合法范围 −40..80,越界丢弃)
- 固件 8.4.7:**[0x04, 温度]** 多字节包,取 byte[1]
- ~~显示 = raw − 6~~(官方 App 有此校准,实测 App 无偏移;以实测为准)

### 3.4 灯光(0x1013)
- 查询:按努比亚官方 App 路径对 `0x1013` 执行 GATT `readCharacteristic`,以 `onCharacteristicRead` 返回值作为状态。
- 灯效未回读到前,轮询按节流间隔(4s)持续重试 read;read 超时(2.5s)不隔离灯光通道,保证慢固件最终能读到。
- 官方数据处理器不剥读回数据的前缀,直接以 byte[0] 作为 mode;常亮/单色呼吸颜色来自 byte[1..3]。
- 模式命令 4 字节 `[mode][R][G][B]`:

| mode 字节 | 语义(官方字符串) |
|---|---|
| `0x01` | 炫彩 colorful |
| `0x02` | 呼吸 breathe |
| `0x03` | 呼吸 breathe(带 0xFF 变体) |
| `0x04` | 常亮 all_on |
| `0x05` | 场景模式(scene;官方读取时为有效状态) |
| `0x06` | 关闭 off |

- 场景下发(官方 `sendScenarioCmd`):light=1→`[01,00,00,00]`,2→`[02,00,00,00]`,3→`[01,FF,00,00]`,4→`[03,FF,00,00]`,5→`[05,00,00,00]`
- 自定义颜色:`[R, G, B]`(3 字节)或 `[R, G, B, 0]`(4 字节,官方示例 `[FF,00,00]`、`[FF,FF,00,00]`)

- 查询当前保护配置:主动读取 0x101F,至少 4 字节 `[flags,eventId,high,low]`;byte0 bit2 表示过冷/冷凝保护。
- 配置状态按特征分别主动读取;回调将原始数据路由到 `Jacket8ProDataHandler`/ViewModel,再由 LiveData 更新 UI。
- 1013 使用 `queryLight` → `readCharacteristic`;1011 hall、1012 fan、1017 overclocking、1018 auto 与 101F 温度保护各自回读,不会用本地预设代替回读。
- ColdFront 对特征不可读或返回无效数据时不伪造状态,相关控制项保持隐藏/未知。

## 4. 连接流程(官方)

1. 扫描(过滤 0x4a41 + MSD 0x8CA)→ `connectGatt(autoConnect=false)`
2. `discoverServices` → `buildServiceAndCharacter`:逐个 getCharacteristic(0x1011…0x101f)
3. 订阅特征支持的通知,并按配置项主动读取
4. `onCharacteristicRead`/`onCharacteristicChanged` → 数据处理器按服务和特征分发 → ViewModel LiveData → Activity 观察并刷新 UI

## 5. 与旧协议(1–6 代)的差异

| 项 | 旧(原 RedmagicCooler 项目) | 8 Pro 实测(本次逆向) |
|---|---|---|
| 识别 | ServiceData(0x4A41) payload 16 字节 UUID | **MSD 0x8CA 的 [mainType, subType]** |
| 风扇 raw | 40–200 | **40–80(8 档)** |
| 自动模式 0x1018 | 一律写 0x00 | **开=0x01,关=0x00** |
| 温度 | data[0] | 1014 / 1015 温度包；实测资料无偏移，当前应用仍保留 −6°C 校准（待核对） |
| RGB | `[effect][R][G][B]` | 模式命令 4 字节 + 自定义 `[R,G,B]` |
