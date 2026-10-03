# 全局缺陷审查与回归说明

## 范围

本轮静态审查覆盖 `ble/`、`domain/`、`data/`、`service/`、`ui/` 全部生产 Kotlin 文件、Manifest、现有测试、协议文档及 Gradle / GitHub Actions 配置。仅修复能够从代码路径确认的缺陷；未进行依赖大版本升级或界面风格重构。

所有编译、单元测试、Android Lint 和 APK 构建均通过 GitHub Actions 执行，不在本地运行 Gradle 或 Kotlin 编译器。CI 的具体结果以本 PR 最新提交对应的检查和上传报告为准。

## 修复清单

| 问题 | 修复 | 回归覆盖 |
|---|---|---|
| 首次读取设置失败时，独立 readiness 协程抛出未处理 IOException | 删除独立 readiness；共用可报告错误、保留最后成功值并重试的状态流 | `SettingsStateTest` 的失败、恢复、取消测试 |
| 设置各字段与 readiness 独立发布，首帧可能混合默认值和磁盘值 | 一个完整 `AppSettings` 快照同时表达就绪、主题、语言和行为设置 | `SettingsRepositoryTest`、`SettingsStateTest` |
| RSSI / CCC 描述符非致命超时后，迟到回调可能误完成下一操作 | 按 owner 身份、target 身份、操作类型隔离超时组合，直到会话释放 | `GattOperationQueueTest` |
| Activity / ViewModel 重建后再次被当成首次前台启动，漏掉后台断链恢复 | 将首次启动标记保存在共享 BLE manager / store，而非 observer 实例 | `BackgroundResumeTest` |
| 空或未知散热开关回包被当成 OFF；无效报文还会阻止成功写入的状态回填 | 仅接受合法控制值；纯 reducer 成功解码后才推进 authoritative revision | `CoolerTelemetryReducerTest` |
| 1015 截断温度包 `[04]` 被当成实际温度样本 | STATUS 按 tag 检查 payload，保留 1014 合法单字节温度语义 | `CoolerTelemetryReducerTest` |
| 不可读特征被计作轮询成功，失败退避无法累计 | 轮询仅统计真正成功的可读目标，保留初始化的跳过语义 | `TelemetryPollTest` |
| 服务启动异步读取期间，目标档案删除事件被丢弃 | 启动命令到达后，目标失效事件统一排入串行校验队列，不依赖 target 已赋值 | `ServiceStateTest` 的可控启动/删除时序 |
| 磁贴开启自动模式时使用旧 active 档案，可能切换到错误设备 | 读取后核对会话、连接意图与档案身份；不允许旧档案替换当前目标 | `ServiceStateTest` |
| 自动模式重试成功但 BLE 状态不变时，通知仍显示失败 | 完成后显式刷新；通知去重同时包含文本、转速及重试按钮 | `ServiceStateTest` |
| RTL 横向拖动 / fling 与页面位移方向相反 | Foundation 统一反转 delta 和 velocity，保留 RTL offset 行为 | `TopLevelDragTest` |
| RGB 页移出视口后，无限预览动画仍在运行 | 页面活跃与 Activity resumed 同时成立才启用动画，不丢弃草稿 | `RgbPreviewStateTest`，保留既有草稿测试 |
| 生命周期合并掉连接中间态后，连接成功不再退出扫描页 | 导航 effect key 包含连接会话 ID，不以 route 为 key | `ConnectionNavigationTest` |
| 中文诊断页 UUID 溢出提示仍显示 `more` | 归入中英文字符串表 | `StringsTest` |

## 验证边界与实机清单

纯逻辑测试不等同于 Android 设备集成测试，也不能证明不存在其他缺陷。以下仍需实机回归：

- Android 24–37 的权限、前台服务、开机恢复及 OEM 后台限制。
- 扫描、重连、蓝牙切换、迟到 GATT 回调和多设备切换。
- 冷启动遇到慢存储、保存的深浅主题/动态色/语言，以及读取失败提示。
- RTL 拖动与 fling、扫描连接后的返回、RGB 隐藏页帧活动和草稿保留。
- 删除服务目标、磁贴点击与连接切换并发、自动模式失败后重试通知。

温度 −6°C 校准及逆向协议文档中的硬件歧义保持不变：本轮没有实机抓包证据，不把这些猜测当作已确认 bug 修改。
