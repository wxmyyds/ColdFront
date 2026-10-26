# ColdFront

红魔散热器（RedMagic Cooler）BLE 控制器，使用 Kotlin 与 Jetpack Compose 编写。项目是 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 的重写版本，非 Nubia/RedMagic/ZTE 官方应用；协议依据官方 App 静态分析及标注的第三方实测资料。

## 功能

- 手动调速、自动温控、超频模式及 RGB 灯效；控件按设备实际 GATT 能力显示。
- 查看温度、转速和信号强度；遥测失效时提示数据可能过期，并提供重连入口。
- 保存多台设备、指定默认连接设备；可通过快捷设置磁贴维持前台连接服务。
- 中文/英文界面，支持浅色、深色和主题色设置。

## 设备支持

| 型号 | 支持情况 |
|---|---|
| 红魔散热器 1–6 代 | 按广播名及厂商数据兼容旧协议 |
| RM Magcooler 6 Pro | 独立协议实现；仅静态分析验证，未完成实机验证 |
| RM Magcooler 6 Pro+ | 6 Pro 家族协议 + 官方 9 档风扇；仅静态分析验证，未完成实机验证；协议见 [6 Pro+ 文档](docs/protocol-6pro-plus.md) |
| RM Magcooler 8 Pro | 含功耗限档等专属行为；协议见 [8 Pro 文档](docs/protocol-8pro.md) |

6 Pro 依据 MSD `(0x05,0x05)/(0x05,0x06)`、广播服务 UUID `4a41` 和名称识别。6 Pro+ 与 6/6 Pro 广播同一组 MSD，仅靠名称（`REDMAGIC Cooler 6 pro+`）区分；协议与 6 Pro 同族，风扇为官方 9 档（40–80 raw 中取 9 个档位格点）。详见 [6 Pro 文档](docs/protocol-6pro.md) 与 [6 Pro+ 文档](docs/protocol-6pro-plus.md)。

全系列统一命名：展示名为 `RM Magcooler {代数}[ 变体]`（如 `RM Magcooler 6`、`RM Magcooler 6 Pro`、`RM Magcooler 8 Pro`），别名为 `Cryo Cooler {代数}[ 变体]`（历史展示名，如 `Cryo Cooler 6`、`Cryo Cooler 6 Pro`）。

## 构建

需要 JDK 21。使用仓库 Gradle Wrapper：

```bash
./gradlew assembleDebug
```

GitHub Actions 在 Pull Request 上运行单元测试、Android Lint，并构建 Debug/Release APK。协议文档区分了静态代码证据、第三方实测和仍待硬件验证的事项。

## 已知问题

部分 `compose-icons` 图标存在路径坐标错误。已确认的问题图标使用 Google 官方矢量资源替代；新增图标时应先检查路径是否位于 viewport 内。

## 许可

基于 MIT 许可的 [Hitomatito/RedmagicCooler](https://github.com/Hitomatito/RedmagicCooler) 重写。
