# 红魔散热器 6 Pro BLE 接口（静态逆向）

> 本文和实现基于 `/home/WXM/AllProjects/smali` 中官方应用的 Smali。当前没有红魔散热器 6 Pro 实机，因此本文记录的是代码证据，不是硬件抓包结论。没有静态证据的字段不会在 ColdFront 中被猜测实现。

## 1. 设备识别

官方 `Jacket6ProManagerV2` 的扫描逻辑（`cn/nubia/device/bluetooth/jacket6/Jacket6ProManagerV2.smali`，`Q6`/`U1`）使用：

- Manufacturer Specific Data 公司 ID `0x08CA`；
- payload 前两个字节为 `[mainType, subType]`；
- `mainType = 0x05`；
- 6 Pro 管理器接受 `subType = 0x05` 或 `0x06`；
- 正常散热器广播还要求 Service UUID `00004a41-0000-1000-8000-00805f9b34fb`；
- `PPlusOTA` 是 OTA 特殊入口，不是正常 6 Pro 控制模式。

由于 `0x05/0x06` 可能同时由普通 6 和 6 Pro 管理器接受，ColdFront 只有在广播名称明确包含 `Pro` 时才把 MSD `0x05/0x06` 归类为 `JACKET_6_PRO`；没有名称证据时保守归类为普通 `JACKET_6`。这避免把两个硬件变体无依据地混为 6 Pro。

## 2. 服务和特征

正常 Fun Service：

```text
Service  d52082ad-e805-9f97-9d4e-1c682d9c9ce6
1011     Hall 模式
1012     Fan / 风扇值
1013     Light / 灯效和颜色
1014     Temperature
1015     Status 状态封装
1016     Logger
1017     Over-clocking / Super Mode
1018     Auto / Speed 相关寄存器
```

官方 `buildServiceAndCharacter` 对正常 6 Pro 硬性要求 `1011`、`1012`、`1013`、`1015`；`1014`、`1017`、`1018` 和设备信息服务按可选项处理。ColdFront 连接发现阶段沿用该边界，并优先使用主服务中的同 UUID 特征。

设备信息服务：

```text
180A / 2A28  软件版本（UTF-8）
180A / 2A26  硬件版本（UTF-8）
```

## 3. 已确认的写入命令

官方 `f2/a` wrapper 最终直接调用基类的 `setValue`、`setWriteType(2)` 和 `writeCharacteristic`。静态代码没有发现 CRC、累加和、异或校验、尾部校验字节或自动长度字段。

| 功能 | 特征 | 官方 payload |
|---|---|---|
| Hall 自动 | `1011` | `[01]` |
| Hall 开启 | `1011` | `[02]` |
| Hall 关闭 | `1011` | `[03]` |
| 风扇 raw | `1012` | 调用者传入的单字节，官方 UI 限制为 `0x28..0x50` |
| 自动温控开 | `1018` | `[01]` |
| 自动温控关 | `1018` | `[00]` |
| Super Mode 开 | `1017` | `[01]` |
| Super Mode 关 | `1017` | `[00]` |
| 灯效/颜色 | `1013` | 颜色页构造的原始数组，不追加字段 |

注意：官方枚举中的 `AUTO_R` 入口实际走的是 GATT 写入 `[01]`，不是 read；关闭入口 `AUTO_W` 写入 `[00]`。这是 Smali 中命名与实际操作不一致的情况，ColdFront 以实际调用链为准。

### 灯效 payload

官方 `f0.t0(lightMode, colorMode, colorValue)` 和 `e0.onLightRead` 证明的模式为：

```text
[01, 00, 00, 00]  炫彩
[02, 00, 00, 00]  呼吸/多色呼吸
[03, R, G, B]     单色呼吸
[04, R, G, B]     常亮
[06, 00, 00, 00]  关闭
```

裸 `[FF,00,00]` 是官方设置页使用的默认 RGB 颜色值，不是可确认的关闭命令。ColdFront 的 `RGBConfig.toCommand()` 使用上表格式。

## 4. 状态解析

6 Pro 的 `1015` 不是 8 Pro 的 `04/05/07/08/09` 标签表，而是 `[kind, payload...]`：

```text
01  Hall，payload[0] = 1/2/3 对应自动/开/关
02  Fan，payload[0] 为风扇 raw
03  Light，去掉外层 kind 后按 1013 灯效数组解析
04  Temperature，去掉 kind 后解析温度 payload
05  OutputHasLoad，官方有回调，但静态代码不能证明各值的布尔映射
```

ColdFront 已独立实现 `Jacket6ProProtocol` 的 1015 解析；未知状态和未证明的 `OutputHasLoad` 值会被忽略，不会生成虚假状态。6 Pro 温度不套用 8 Pro 专属的 `-6°C` 校准；官方 6 Pro 温度回调的完整数值换算仍需硬件或更多资源证据核对，因此当前只按原始温度字节解析。

## 5. 功能边界

静态代码确认官方协议和父类 Activity 存在 Hall 自动/开启/关闭回调（`rd_hall_auto`、`rd_hall_on`、`rd_hall_off`）。但 6 Pro 子类没有新增独立设置页；这些控件是否在具体官方版本的主页面布局中可见，当前无法仅凭 Smali 资源完整确认。

ColdFront 因此保留 Hall 三态协议和状态/命令封装，但不新增一个未经确认的首页 Hall 下拉设置项；首页仍使用现有通用散热开关。

静态代码确认官方协议支持：

- Hall 自动/开启/关闭；
- 40–80 raw 风扇滑条；
- 自动温控；
- Super Mode/超频；
- 灯效和颜色；
- 温度、风扇和状态读取；
- 软件/硬件版本读取入口。

ColdFront 复用通用 GATT 串行队列、连接/断线重连、配置回读和 StateFlow UI，但 6 Pro 的识别、40–80 raw 规则、Hall 三态和 1015 状态封装均在 `Jacket6ProProtocol.kt` 中独立实现。Hall 命令可由协议层发出，但当前 UI 不把它包装成新的官方设置项。8 Pro 的功耗限档和离散 8 档规则不会应用到 6 Pro。

## 6. 无法静态确认、必须真机验证

- `0x05/0x05` 与 `0x05/0x06` 在实际零售硬件上的对应关系；
- 不同固件对特征 properties、通知和读回时序的差异；
- 温度的单位、缩放和校准；
- `OutputHasLoad` 的具体值映射及其是否影响控制；
- 颜色页在所有固件上允许的 `colorValue` 长度；
- 风扇越界值在设备端的处理；
- 断线后的固件状态是否需要额外初始化；
- OTA 服务、握手和升级协议。

因此当前分支不声称完成真机认证；上述项目应在获得设备后进行硬件回归。
