# 红魔散热器 8 Pro BLE 接口(逆向自官方 App smali)

> 来源:`cn.nubia.externdevice` 官方应用 smali 反编译(`cn/nubia/device/bluetooth/jacket8pro/`)。
> 管理类 `Jacket8ProManagerV2`(继承 `Jacket3ManagerV2`)、处理器 `Jacket8ProProcessor`、数据处理器 `Jacket8ProDataHandler`。

## 1. 设备识别(广播)

| 项 | 值 |
|---|---|
| 广播 Service UUID | `00004a41-0000-1000-8000-00805f9b34fb`(家族标记,须在 ServiceUuids 列表中) |
| 厂商数据(Manufacturer Specific Data) | Company ID **0x08CA(2250)**,payload 前 2 字节 = `[mainType, subType]` |
| 8 Pro 识别码 | **mainType = 0x05, subType = 0x08** |
| 全系识别码 | 3=(5,3) 4=(5,4) 5/5Lite=(5,5) 6/6Pro=(5,6) 8Pro=(5,8) |
| OTA 模式名 | BLE 名含 `PPlusOTA`(识别放行) |
| Device 枚举 | `JACKET8Pro`,设备代码 `rm_charge_clip8_pro` |

## 2. GATT 结构

主服务(全系共用):`d52082ad-e805-9f97-9d4e-1c682d9c9ce6`

| 特征值 | 语义 | 属性 |
|---|---|---|
| `00001011-...` | HALL 霍尔(磁吸检测) | R/W |
| `00001012-...` | **FAN 风扇调速** | R/W |
| `00001013-...` | **LIGHT 灯光** | R/W/Notify |
| `00001014-...` | (保留) | R |
| `00001015-...` | **TEMPERATURE 温度** | Notify |
| `00001016-...` | LOGGER 日志(UTF-8 文本) | R/Notify |
| `00001018-...` | **AUTO 自动模式开关 / SPEED** | W |
| `00001019-...` | FAN_SPEED_NEW 风扇转速(新) | R/Notify |
| `0000101a-...` | FAN_POWER 风扇电源 | R |
| `0000101b/1c/1d-...` | CHARGE / 场景等 | R |
| `0000101f-...` | TEMPERATURE_WARNING 温度告警/保护阈值 | W |

另含标准 Device Information 服务 `0000180a-...`(`0x2a26` 固件版本、`0x2a28` 软件版本)。

写类型:`WRITE_TYPE_DEFAULT(2)`。

## 3. 核心命令

### 3.1 风扇调速(0x1012)
- 写**单字节 raw**,8 Pro 范围 **40(0x28)–80(0x50)**,8 档
- 换算:`raw = 40 + percent × 40 / 100`
- UI 档位映射(官方 Companion.d):40–42→0档,43–48→1,49–54→2,55–60→3,61–66→4,67–70→5,71–74→6,≥75→7
- 读取/Notify 回读当前 raw

### 3.2 自动模式(0x1018)
- 开:**写 `0x01`**(官方日志"8PRO 背夹 writeAutoOn 写1")
- 关:**写 `0x00`**("writeAutoOff 写0")

### 3.3 温度(0x1015 Notify)
- 单字节**有符号** °C
- **显示值 = raw − 6**(官方做 -6°C 校准)

### 3.4 灯光(0x1013)
- 查询/握手:写单字节 `0x11`,随后 Notify/Read 回当前状态
- 模式命令(4 字节 `[mode, 0/0xFF, 0, 0]`):

| mode 字节 | 语义(官方字符串) |
|---|---|
| `0x01` | 炫彩 colorful |
| `0x02` | 呼吸 breathe |
| `0x03` | 呼吸 breathe(带 0xFF 变体) |
| `0x04` | 常亮 all_on |
| `0x05` | 场景模式 5 |
| `0x06` | 关闭 off |

- 场景下发(官方 `sendScenarioCmd`):light=1→`[01,00,00,00]`,2→`[02,00,00,00]`,3→`[01,FF,00,00]`,4→`[03,FF,00,00]`,5→`[05,00,00,00]`
- 自定义颜色:`[R, G, B]`(3 字节)或 `[R, G, B, 0]`(4 字节,官方示例 `[FF,00,00]`、`[FF,FF,00,00]`)

### 3.5 温度告警/防凝露保护(0x101f)
- 写 4 字节配置(告警阈值等),配 ACK 应答流程

## 4. 连接流程(官方)

1. 扫描(过滤 0x4a41 + MSD 0x8CA)→ `connectGatt(autoConnect=false)`
2. `discoverServices` → `buildServiceAndCharacter`:逐个 getCharacteristic(0x1011…0x101f),缺失记 "can not find fan/light/… characteristic"
3. 订阅 0x1015/0x1013/0x1019 等通知
4. 写 0x11 到 0x1013(灯握手)→ 回读状态
5. 档位/温度/转速全部经 LiveData 推送到 UI

## 5. 与旧协议(1–6 代)的差异

| 项 | 旧(原 RedmagicCooler 项目) | 8 Pro 实测(本次逆向) |
|---|---|---|
| 识别 | ServiceData(0x4A41) payload 16 字节 UUID | **MSD 0x8CA 的 [mainType, subType]** |
| 风扇 raw | 40–200 | **40–80(8 档)** |
| 自动模式 0x1018 | 一律写 0x00 | **开=0x01,关=0x00** |
| 温度 | data[0] | data[0] − 6 |
| RGB | `[effect][R][G][B]` | 模式命令 4 字节 + 自定义 `[R,G,B]` |
