# 整体代码审查记录

审查基线：`a7019daa34120ca4bac5dc22ed18ad7d12bfab95`（`main`）。

## 范围与原则

覆盖 BLE 扫描/连接/GATT 队列、设备协议与状态归约、DataStore、ViewModel、后台服务/磁贴、页面导航/状态恢复、主题和构建依赖。仅修改可从代码确认的问题；不调整页面布局、主题配色、设备协议常量或温度校准，不进行整页或全架构重写。

## 确认问题与修复

| 问题及触发条件 | 修改位置 | 回归保护 |
|---|---|---|
| 非永久隔离的 GATT 操作超时后，同类新操作可能接收前一次迟到回调 | `ble/GattOperationQueue.kt`：保留超时标记；旧回调未排空时禁止复用，排空回调不提交值；永久隔离不会解锁 | `GattOperationQueueTest`：超时→重试被阻止→旧回调排空→新请求/新回调；身份和操作类型隔离、升级隔离、清理 |
| Smart/Boost 独立命令缺少互斥事务边界；关闭另一模式失败仍继续开启；在途 ON 不在回读快照中 | `ble/ModeCommandCoordinator.kt`、`CoolerBleManager.kt`：共享最新意图、串行 OFF/ON、保守记录可能在途的 ON、失败与被替代时中止后续写入 | `ModeCommandCoordinatorTest`：双向并发、失败/不可用、意图替换、断开、排队时限档、限档安全关闭不覆盖 Smart 意图、取消等待 |
| 写成功后等待回读期间断开/被替代，命令仍报告原写成功 | `executeFreshCommand`：回读完成后再次检查会话及命令有效性 | 同上：直接测试生产完成路径 |
| 无效配置报文被无条件标记为已确认，默认 OFF 值因此解锁控件 | `ble/ConfigurationState.kt`：仅确认成功解析的配置；保留未知 RGB 模式可编辑/恢复的既有例外 | `ConfigurationStateTest`：空包、非法开关值、合法 OFF、重复回报、RGB 例外 |
| 初始化期间收到供电限档，写入因未就绪被拒绝，进入 CONNECTED 后不补执行 | 就绪转换及相关配置首次确认时重新检查限档，重复限档包保持不重复触发 | `ConfigurationStateTest`：DISCOVERING→CONNECTED、迟到配置、重复包和不限档 |
| UI/磁贴的手动请求在读盘或服务队列延迟后可作用于同地址的新会话 | `service/ManualControlTarget`、`CoolerService.kt`、`CoolerViewModel.kt`：传递地址及会话身份，在异步边界后复核；服务封装 Intent | `ServiceStateTest`：相同地址重连、换设备、断开、读盘/服务队列延迟 |
| RGB 写成功但回读失败时，SENT 加旧快照会覆盖草稿并清除发送状态 | 有效 RGB 报告递增版本；实际写入分派时记录基线，仅新的回读版本可释放草稿 | `RgbEditorStateTest`、`ConfigurationStateTest`：旧快照、写前遥测、回调与协程恢复之间的通知、相同值新报告、跳帧回读 |
| 已连接时打开扫描页，旋转恢复后误当成新连接而弹回首页 | 保存并比较上一次已观察的连接键，而非每次组成首次执行都导航 | `ConnectionNavigationTest`：同会话恢复、新会话成功、遗漏中间态 |
| 已确认的功率限制对话框在旋转后重复弹出 | 保存包含会话身份的确认记录，恢复时校验会话，保留解除/变化后再次提醒的规则 | `PowerLimitNoticeStateTest`：保存/恢复和提示状态转换 |
| 档案 JSON 损坏时，即使已清除默认/后台服务引用，读取仍先解析无关文档而失败 | `data/ProfileRepository.kt`：引用不存在先返回 null；存在时仍严格解析，不覆盖损坏文档 | `ProfileRepositoryTest`：清除后 load/Flow、原文保留、有效引用仍抛错 |

## 有限解耦与清理

- 将纯字符串调色板键移到 data 层，消除设置仓库反向依赖 UI；键值、顺序、默认值和配色算法不变。
- BLE 只持有一份配置确认状态；模式协调和配置确认规则提取为无 Android 依赖的可测试逻辑。
- UI/磁贴不再自行拼装手动服务 Intent；Boost 的协议互斥统一由 BLE 管理，UI 仅取消后台恢复意图。
- 移除无调用的设置单项 Flow、诊断 DTO 中只写不读的 `BluetoothDevice`、信号质量计算、通知权限数组辅助函数及 `edit/error` 文案。
- 清理没有任何仪器测试源码/CI 消费的测试依赖和 runner，以及未引用的版本目录条目；保留实际使用或传递依赖尚不能排除的库，不盲目升级依赖。

## 全局同步机制复查

同一 PR 后续进一步检查 Timer、锁、延时、轮询及状态归属，具体替代方案、保留理由和验证边界见 [同步机制复查](synchronization-review.md)。

## 验证方式与边界

按要求不在本地执行 Gradle、编译或单元测试。本地仅进行差异复核、`git diff --check`、XML/TOML 解析及引用检查；正式验证通过新分支 PR 的 GitHub Actions 执行：

- `testDebugUnitTest testReleaseUnitTest`
- `lintDebug lintRelease`
- `assembleDebug assembleRelease`

具体运行链接及结果以 PR 检查记录为准。纯 JVM 测试验证的是状态与事件序列，不代替 Android 真机/BLE 硬件验证。温度 −6°C 校准、旧型号未确认寄存器、实际 GATT 固件行为和视觉效果均未宣称经过实机回归；建议实机重点回归旋转恢复、Smart/Boost 快速切换、后台服务手动停止、连接时已限功率、RGB 回读超时和重连恢复。
