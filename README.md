# ColdFront

红魔散热器（Redmagic Cooler）BLE 控制器 —— 基于 **Material Design 3 Expressive (MD3E)** 全面重写的 Android 应用。

> 本项目是 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 的完全重写版本，非官方应用，与 Nubia/RedMagic/ZTE 无关。BLE 协议逆向自官方 App `cn.nubia.externdevice`。

## 功能

- **手动调速**：0–100% 风扇转速滑块（BLE raw 40–200）
- **自动模式**：前台服务持续温控，按温度阈值自动调速（低温/中温/高温三档 + 防凝露保护）
- **RGB 灯效**：炫彩 / 全彩呼吸 / 单色呼吸 / 常亮 / 关闭，RGB 调色
- **实时监控**：散热器温度通知、转速回读、信号强度
- **多设备档案**：DataStore 持久化，开机自动恢复自动模式
- **快捷设置磁贴**：一键开关自动模式
- **双语**：中文 / English（100% Kotlin 字符串表，无 strings.xml）
- **动态取色**：主题色无条件跟随系统壁纸（Android 12+），旧系统回退冷蓝品牌色

## 支持的设备

| 型号 | 代数 | 识别方式 |
|---|---|---|
| 双核散热背夹 / 涡轮 / 磁吸散热器 | 1–3 | ServiceData UUID |
| Heat Sink 4 Pro / 5 Pro / 5 Lite / 6 / 6 Pro | 4–6 | ServiceData UUID |
| Heat Sink 7 / 7 Pro | 7 | 广播名兜底（UUID 待确认） |
| **Cryo Cooler 8 Pro** | 8 | **广播名兜底（UUID 待确认）** |

### ⚠️ 关于散热器 8 Pro

8 Pro（第 8 代 COLDLNG 架构，36W，11 叶 6200RPM，16 颗可寻址 RGB，NTC 温控）目前靠蓝牙广播名识别。全系列共用同一套 GATT 服务/特征，连接与控制已可用；如需精确识别，请按以下步骤补全广播 UUID：

1. 用 **nRF Connect** 扫描 8 Pro，查看其广播包
2. 找到 **ServiceData（键 0x4A41）** 的 payload（前 16 字节即设备专属 UUID）
3. 填入 `domain/CoolerDeviceType.kt` 中 `JACKET_8_PRO.advertisingUUID` 的占位值

## 技术栈

- **Kotlin 100%**（UI 全 Jetpack Compose；XML 仅剩 `AndroidManifest.xml` 与一个矢量启动图标）
- **MD3E**：`MaterialExpressiveTheme` + `MotionScheme.expressive()` 弹簧动效 + Expressive 形状阶梯
  - 依赖 `androidx.compose.material3:material3:1.5.0-alpha28`（经 `compose-bom-alpha:2026.09.00` 托管）
- **架构**：domain / ble / data / thermal / service / ui 分层，`StateFlow` 驱动 UI
- **DataStore** 持久化（档案 + 温控阈值）
- Android 12 以下走旧 BLE 权限/旧 GATT 回调重载，兼容 API 24–37

## BLE 协议（逆向）

| 项 | UUID |
|---|---|
| 广播 ServiceData 键 | `00004a41-0000-1000-8000-00805f9b34fb` |
| Fan 主服务 | `d52082ad-e805-9f97-9d4e-1c682d9c9ce6` |
| 风扇转速 | `00001012-...`（40–200 raw ↔ 0–100%） |
| 温度通知 | `00001015-...`（notify，单字节有符号 °C） |
| RGB 灯控 | `00001013-...`（写入 `[effect][R][G][B]`） |
| 自动模式 | `00001018-...`（写 `0x00`） |

## 构建

要求：Android Studio（AGP 9.2.1 / Kotlin 2.3.21）、compileSdk 37、minSdk 24。

```bash
git clone https://github.com/wxmyyds/ColdFront.git
cd ColdFront
./gradlew assembleDebug
```

> 注意：MD3E 完整 API 需要 material3 alpha 线（已在 `gradle/libs.versions.toml` 配置 `compose-bom-alpha`）。

## 目录结构

```
app/src/main/java/io/github/wxmyyds/coldfront/
├── domain/    设备类型(含 8 Pro)、BLE 常量、模型、档案
├── ble/       BLE 控制器(扫描/连接/GATT) + 权限
├── data/      DataStore 仓库(档案/阈值)
├── thermal/   CPU/电池温度监控
├── service/   前台服务、开机恢复、磁贴、Worker
└── ui/        MD3E 界面(主题/双语/各屏)
```

## 许可

基于 MIT 许可的 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 重写。
