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

| 型号 | 代数 | 识别方式（厂商数据 MSD 0x08CA 的 [mainType, subType]） |
|---|---|---|
| 双核/涡轮散热背夹 | 1–2 | 广播名兜底（旧协议） |
| 磁吸散热器 / Heat Sink 4 Pro / 5 Pro / 5 Lite / 6 / 6 Pro | 3–6 | MSD (5,3) (5,4) (5,5) (5,6) |
| **Cryo Cooler 8 Pro** | 8 | **MSD (5,8)，官方协议完整支持** |

### 散热器 8 Pro（官方 App smali 逆向，协议已确认）

- **识别**：广播厂商数据（Company ID 0x08CA）前两字节 `[0x05, 0x08]`；广播 Service UUID 含 `00004a41-...`
- **风扇**：单字节 raw，**40–80 共 8 档**（`raw = 40 + percent×40/100`）
- **自动模式**：0x1018 写 **0x01 开 / 0x00 关**
- **温度**：0x1015 通知，单字节有符号 °C，**显示值 = raw − 6**
- **灯光**：0x1013 写 `[mode][R][G][B]`（1炫彩/2呼吸/3单色呼吸/4常亮/6关），查询写 0x11

完整协议文档：[docs/protocol-8pro.md](docs/protocol-8pro.md)

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
| 广播 Service UUID / MSD | `00004a41-...` / Company `0x08CA`=[mainType,subType] |
| Fan 主服务 | `d52082ad-e805-9f97-9d4e-1c682d9c9ce6` |
| 风扇转速 | `00001012-...`（单字节 raw；8 Pro 40–80，旧型号 40–200） |
| 温度通知 | `00001015-...`（单字节有符号 °C，显示 = raw − 6） |
| RGB 灯控 | `00001013-...`（`[mode][R][G][B]`，查询写 0x11） |
| 自动模式 | `00001018-...`（0x01 开 / 0x00 关） |

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
