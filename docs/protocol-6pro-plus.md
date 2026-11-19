# 红魔散热器 6 Pro+ 协议

本文依据新版官方 App 的 smali 静态代码整理（`cn/nubia/device/bluetooth/jacket6` 的 V2 架构：
`Jacket6ProPlusManagerV2` / `Jacket6ProProcessor` / `Jacket6ProDataHandler`）。由于没有 6 Pro+ 实机，文中的结论不能视为硬件验证结果。旧版官方 App 不支持 6 Pro+ 的结论（见 [6 Pro 文档](protocol-6pro.md)）只适用于旧版 dump；新版 App 有独立的 `Device.JACKET6ProPlus` 枚举和专属管理器。

## 识别与 GATT

官方 6 Pro+ 扫描过滤（`Jacket6ProPlusManagerV2.S6`/`d2`）接受 MSD `(0x05,0x05)` 与 `(0x05,0x06)`，并要求广播服务 UUID `00004a41-…`。这些条件与 6/6 Pro 完全重叠；OTA 模式还接受广播名 `PPlusOTA`。

型号只通过广播名区分：`DeviceHelperKt.X()` 判定名称是否包含 `REDMAGIC Cooler 6 pro+`（忽略大小写）。ColdFront 使用同族判别，要求名称含 `6`、`Pro`，并含 `Plus`/`+`，再将明确的 Pro+ 名称识别为独立型号。

GATT 与 6 Pro 同族：主服务 `d52082ad-e805-9f97-9d4e-1c682d9c9ce6`，特征
`1011` Hall、`1012` 风扇、`1013` 灯效、`1014` 温度、`1015` 状态、`1016` 日志、
`1017` 超频、`1018` 自动温控；`180a`/`2a26`/`2a28` 为软硬件版本。6 Pro+ 不使用 8 Pro 的
`101C`/`101D`/`101F`。命令语义与旧版分析一致：`1011` 写 `01/02/03`（Hall 自动/开/关）、
`1012` 写单字节风扇 raw、`1013` 写 `[mode,R,G,B]`、`1017` 写 `01/00`（超频，
官方 V2 的读取回调 `onOverclockingModeRead` 即来自 1017）、`1018` 写 `01/00`（自动温控，
日志 `writeAutoOn 写1`/`writeAutoOff 写0` 与 6 Pro 相同）。

## 风扇：9 档而非连续滑条

官方 Pro+ 界面提供 9 档风扇（`Jacket6ProPlusManagerV2$a` 的 `c(I)`/`d(I)`）：

| 档位索引 | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| raw | 0x28 | 0x2e | 0x34 | 0x3a | 0x40 | 0x44 | 0x48 | 0x4c | 0x50 |
| （十进制） | 40 | 46 | 52 | 58 | 64 | 68 | 72 | 76 | 80 |

`c(I)` 的 raw→档位边界为 40–43/43–49/49–55/55–61/61–67/67–71/71–74/74–78/78+。
ColdFront 使用 1–9 档 UI，任意目标 raw 都会先归一到这 9 个格点再下发。目前没有证据表明 Pro+ 固件接受“连续滑条”的其他值，因此不会写入档位格点之外的值。

## 状态与温度

`1015` 通知仍采用 `[kind, payload...]`（`Jacket6ProDataHandler`）：

| kind | 含义 |
|---|---|
| `01` | 状态变更（官方回调 `onStatusChange`；Hall 值语义与 6 Pro 一致） |
| `02` | 风扇 raw（`onFanRead`） |
| `03` | 灯效 |
| `04` | 温度（`onTemperature`） |
| `05` | OutputHasLoad（`onOutputHasLoad`） |

Pro+ 的 OutputHasLoad 比 6 Pro 多一档：`0x80`/`0x81` → 有负载，`0x00`/`0x01` → 无负载，
**`0x7f` 官方识别但对应协程为空实现**（不变更状态）；其余值不处理。

新版 App 另有独立的 `1014` 温度特征与 `TEMPERATURE` 通道（`queryTemperature` 读路径）。周期温度通知仍走 `1015 kind=04`；ColdFront 沿用 6 Pro 路径，读取首字节 signed，不使用 8 Pro 的 −6°C 校准，超界值会忽略，也不使用 1014 直读。

## ColdFront 的实现口径

- 识别：MSD `(0x05,0x05)`/`(0x05,0x06)` + `4a41` 服务 + 名称判别；连接只绑定主服务，跳过 8 Pro 专属的 `101C`/`101D`/`101F`。
- 风扇：1–9 档（`CoolerBleConstants.gearToRaw6ProPlus` 等），写入一律档位归一。
- 其余命令/遥测与 6 Pro 共用 `Jacket6ProProtocol` 路径。

## 待实机确认

- 零售 6 Pro+ 的实际广播名与两组 MSD 的对应关系；`PPlusOTA` 是否出现在量产固件。
- 档位格点以外的 raw 是否被固件接受（本 App 不写）；温度单位/校准；`1014` 直读的返回格式。
- 断线重连后的状态同步及 OTA 行为；本文不描述或实现 OTA 协议。
