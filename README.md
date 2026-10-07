# ColdFront

红魔散热器（Redmagic Cooler）BLE 控制器 —— 基于 **Material Design 3 Expressive (MD3E)** 全面重写的 Android 应用。

> 本项目是 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 的完全重写版本，非官方应用，与 Nubia/RedMagic/ZTE 无关。BLE 协议逆向自官方 App `cn.nubia.externdevice`。

## 功能

- **手动调速**：0–100% 风扇转速滑块（BLE raw 40–200）；按实际 GATT 能力显示控件，无独立散热开关的设备仍可调速
- **智能温控**：由散热器固件自主调速；快捷磁贴可启用前台服务保持连接，开机仅恢复用户明确启用且未停止的服务。与「破坏神」互斥（对齐官方：开一个会自动关掉并禁用另一个）
- **供电功率限制**：8 Pro 上报充电器供电功率不足（0x1015 标签 0x7）时提示用户，把制冷档位上限压到限档、把当前档位下发回限档，并禁止开启「破坏神」
- **RGB 灯效**：炫彩 / 全彩呼吸 / 单色呼吸 / 常亮 / 关闭，RGB 调色
- **实时监控**：散热器温度通知、转速回读、信号强度；遥测读取超时隔离后提示数据可能过期，可从首页或服务通知重新连接恢复
- **多设备档案**：成功连接后保存，按 MAC 去重；重连更新最近使用记录
- **默认连接设备**：在设置页为已保存设备指定「默认连接」，应用启动时自动直连该设备（已有会话时不抢占，失败不重试）
- **快捷设置磁贴**：一键开关自动模式
- **双语**：中文 / English（100% Kotlin 字符串表，无 strings.xml）
- **主题**：默认紫灰配色，支持浅色/深色与三种调色板；Android 12+ 可选壁纸动态强调色，页面/顶栏/底栏/选项背景保持固定

## 支持的设备

| 型号 | 代数 | 识别方式（厂商数据 MSD 0x08CA 的 [mainType, subType]） |
|---|---|---|
| 双核/涡轮散热背夹 | 1–2 | 广播名兜底（旧协议） |
| 磁吸散热器 / Heat Sink 4 Pro / 5 Pro / 5 Lite / 6 / 6 Pro | 3–6 | MSD (5,3) (5,4) (5,5) (5,6) |
| **Cryo Cooler 8 Pro** | 8 | **MSD (5,8)，官方协议完整支持** |

### 散热器 8 Pro（官方 App smali 逆向，协议已确认）

- **识别**：广播厂商数据（Company ID 0x08CA）前两字节 `[0x05, 0x08]`；广播 Service UUID 含 `00004a41-...`
- **风扇**：单字节 raw，**40–80 共 8 档**（`raw = 40 + percent×40/100`）；档位显示按协议的非等距 raw 边界，1/8 档覆盖 raw 40/80
- **自动模式**：0x1018 写 **0x01 开 / 0x00 关**；与 0x1017「破坏神」双向互斥
- **供电功率限档**：`0x1015` 标签 `0x7` 的 byte1 是官方档位索引 **0–8**（8 = 不限档，小于 8 = 供电不足）；限档索引 → raw 见协议文档 3.5
- **温度**：`0x1014` 温度与 `0x1015` 状态包；当前实现保留既有 −6°C 校准，协议资料存在冲突，待实机对照确认
- **灯光**：0x1013 写 `[mode][R][G][B]`（1炫彩/2呼吸/3单色呼吸/4常亮/6关），查询写 0x11

完整协议文档：[docs/protocol-8pro.md](docs/protocol-8pro.md)

## 技术栈

- **Kotlin 100%**（UI 全 Jetpack Compose；XML 仅剩 `AndroidManifest.xml` 与一个矢量启动图标）
- **MD3E**：`MaterialExpressiveTheme` + `MotionScheme.expressive()` 弹簧动效 + Expressive 形状阶梯
  - 依赖 `androidx.compose.material3:material3:1.5.0-alpha29`（经 `compose-bom-alpha:2026.09.01` 托管）
- **架构**：domain / ble / data / service / ui 分层，`StateFlow` 驱动 UI；Service 共享 BLE 管理器
- **DataStore** 持久化（设备档案、外观设置、启动默认设备、明确启用的后台服务目标）
- Android 12 以下走旧 BLE 权限/旧 GATT 回调重载，兼容 API 24–37

## BLE 协议（逆向）

| 项 | UUID |
|---|---|
| 广播 Service UUID / MSD | `00004a41-...` / Company `0x08CA`=[mainType,subType] |
| Fan 主服务 | `d52082ad-e805-9f97-9d4e-1c682d9c9ce6` |
| 风扇转速 | `00001012-...`（单字节 raw；8 Pro 40–80，旧型号 40–200） |
| 温度 / 状态 | `00001014-...` / `00001015-...`（详见协议文档与校准说明） |
| RGB 灯控 | `00001013-...`（`[mode][R][G][B]`，查询写 0x11） |
| 自动模式 | `00001018-...`（0x01 开 / 0x00 关） |

## 构建

要求：JDK 21、AGP 9.4.1 / Kotlin 2.4.20、Gradle 9.6.0、compileSdk 37.1、minSdk 24。

```bash
git clone https://github.com/wxmyyds/ColdFront.git
cd ColdFront
./gradlew assembleDebug
```

> 正式验证使用 GitHub Actions：Debug/Release 单元测试、完整 Android Lint 和双变体 APK 构建；测试与 Lint 报告随构建产物上传。硬件连接和实际 UI 效果仍需实机回归。
>
> MD3E 完整 API 需要 material3 alpha 线（已在 `gradle/libs.versions.toml` 配置 `compose-bom-alpha`）。

## 已知问题：图标库部分矢量坐标损坏

图标来自 [`com.composables` Material Symbols](https://github.com/composablehorizons/compose-icons)（`icons-material-symbols-rounded[-filled]`，当前 2.2.1）。**该库有未修复的上游 bug：部分图标的 path 坐标生成错误——Y 整体偏移出 viewport，渲染时图形缺失/越界/错位**，只在个别图标上出现，不代表全部。

- 上游 open issues：#25 Strange icons behavior、#13/#16/#17 同类、#15 Settings icon is distorted、#27 Solid icons fill inner cutouts（均为 open，2.2.1 已是 Maven Central 最新版）
- 已确认受害的图标（含上游用户报告）：`arrow_back`、`check`/`done`/`check_circle`/`check_small`/`verified`（对勾系全家）、`style`、`arrow_back_ios`、`keyboard_arrow_left`、`chevron_left`、`navigate_before`、`turn_left`、`chat_bubble`、`energy_savings_leaf`、`disabled_by_default`、`tag` 及部分 outlined 变体

**根因**：composables 搬运 Google Symbols 素材时，把部分图标的 path Y 坐标整体减了 960（图形被移出 960 viewport，渲染时缺失/越界），官方仓库 `google/material-design-icons → symbols/android/*/materialsymbolsrounded/*_fill1_24px.xml` 里同一图标的坐标是正常的。

**规避策略**：新增图标前先用脚本检查 path 是否出界（任何绝对坐标落在 0–960 viewport 之外即判定损坏）；遇损坏的直接采用 **Google 官方仓库同图标 XML**（`res/drawable/ms_*.xml`），仅去掉 `android:tint=?attr/colorControlNormal` 一行（AppCompat-free 项目，由 Icon composable 着色），坐标保持官方原样。本项目当前实际用到的 35 个库图标中 3 个损坏，均以官方 XML 替代：`arrow_back`→`ms_arrow_back_fill1_24`（保留官方 `autoMirrored`，顺带修复 RTL）、开关/下拉对勾 `check`→`ms_check_fill1_24`、设置页调色板 `style`→`ms_style_fill1_24`。

## 目录结构

```
app/src/main/java/io/github/wxmyyds/coldfront/
├── domain/    设备类型(含 8 Pro)、BLE 常量、模型、档案
├── ble/       BLE 控制器(扫描/连接/GATT) + 权限
├── data/      DataStore 仓库、档案 JSON 编解码
├── service/   前台连接服务、开机恢复、快捷磁贴
└── ui/        MD3E 界面(主题/双语/各屏)
```

## 许可

基于 MIT 许可的 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 重写。
