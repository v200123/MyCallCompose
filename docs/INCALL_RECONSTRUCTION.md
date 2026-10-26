# 通话页还原记录

## 分析目标

来源：`/Users/liuchang/Documents/AndroidProject/fanbianyi/电话 2026-10-23 20-28-59 348.apk`。

- 包名：`com.android.incallui`
- 版本：16.22.0（16022000）
- SHA-256：`cee306e6cef57e3aab5018dfb4ff47fdb7c27fef7de3aa3c68895759876745a1`

使用 REA/JADX 读取类和方法，使用 Android SDK 自带 aapt2 解码布局、尺寸、颜色及矢量资源。没有执行或替换原版系统通话组件。静态代码能说明调用关系，不能证明所有设备上的实际显示与系统行为。

原始资源解码与证据包保存在 `/Users/liuchang/Documents/AndroidProject/fanbianyi/analysis/incallui-16.22.0`。

## 已实现

- 沿用姓名、号码、归属地及右侧 70dp 头像的布局，替换为 APK 的人像矢量，接入本应用保存的联系人名称。
- 按 `call_button_circle.xml` 和 `ShadowedImageView.s` 将普通按钮改为 66dp 半透明圆面，选中时白底黑图。提取静音、扬声器、保持、录音、拨号盘的独立选中资源；增加 50ms 按下收缩和 150ms 恢复。
- 根据 `oplus_dialpad_fragment.xml`、`item_dialpad_button.xml`，将通话拨号盘改为三列圆键，66dp 直径、8dp 行间留白、32dp 数字、11dp 字母、40sp 输入显示。支持本地按键音；收起后重新打开保留本次输入，最多 256 字符。
- 呼叫阶段默认禁用保持和视频，移除它们触发无人接听/忙线提示的错误默认动作；显式自定义跳转仍按规则执行。
- 添加通话、视频和 AI 助手在未配置跳转时给出不可用反馈。
- 新增单调时钟计时模型：保持期间继续计时；后台返回不重置当前状态倒计时；自定义直接进入“已挂断”时也冻结并保存接通时长。
- 保留现有状态编辑、彩铃、媒体、提示音、自动挂断和语音挂断功能。
- 后续已将通话页接入统一规则执行器，移除手动接通，按时长和下一状态自动推进；详见 [通话规则自动执行](CALL_RULE_EXECUTION.md)。

## 证据索引

| 内容 | REA Evidence ID |
| --- | --- |
| 包信息与资源概览 | ev_51c3aa40cb73d0b3abe27874bf018db24ac69d4ec1dba7916430f79793295b08 |
| 按钮绑定、普通与选中资源 | ev_00f424996d29a36f072f51d986be1cd651f52e49b2fd7cd8840eaff2a6ef774c |
| 选中按钮填充白色 | ev_f334653bc4dcb18c80d8bc981002c92f85db167dedad3f1c8798084984474e1b |
| 挂断防重复、收起拨号盘 | ev_58f1182c6cd354cbe268d9c5049b3ee106cbfb42c4991bc6e9ebe4b05060727f |
| 原版静音调用 TelecomAdapter | ev_cf65f403cba9af60ed9f77b589ef322030a459231452ef425e69ebff225ffe96 |
| 原版保持调用 TelecomAdapter | ev_b596ead6a49a788e9a13a3515b88e4300bda8965b6457df8b24c304e6241a170 |

## 验证

- 整包 `:app:assembleDebug` 通过。
- `:app:testDebugUnitTest` 全部 30 项通过，其中通话计时专项测试 9 项、规则自动执行及中断测试 12 项。
- 独立预览包构建通过，包名 `com.lastcoffee.top.oppo_call_compose.preview`。产物：`app/build/outputs/apk/preview/phone-incall-preview.apk`。
- 上一轮界面版本已在 RMX5200 真机（1440×3136，640dpi）安装独立预览版并启动。通过应用拨号盘输入 10086，进入本地模拟通话；已确认呼叫页的右侧头像、归属地、禁用按钮、扬声器和挂断布局。截图为分析目录下 `preview-checks/outgoing.png`。
- 点击“接通”后出现预览应用的麦克风权限弹窗；后续观测到已挂断状态及 00:25 时长。期间设备连接再次中断，未完成保持、拨号盘、返回记录页的连续交互验证；这部分不能据此声称通过。未拨打真实电话。
- 真机恢复连接后，自动执行版本的三个设备集成场景全部通过；覆盖自动流程、保持、拨号盘、操作中断和旧分支失效，记录详见 [通话规则自动执行](CALL_RULE_EXECUTION.md)。验证使用临时规则，未改变用户持久配置。完整截图位于分析目录下 `rule-verification/`。

## 范围与差异

这是当前应用内的模拟通话页，未接入系统 Telecom/InCallService。静音、扬声器、录音按钮保留本地界面状态，不控制真实电话，也不生成通话录音；拨号盘只提供本地反馈，不向真实通话发送 DTMF。系统视频通话、会议、AI 助手和运营商能力没有移植。

普通应用无法直接取得原版使用的 OPPO 私有窗口、光效和电话权限。背景沿用项目已有壁纸与系统模糊机制，按钮复现资源定义的主体颜色及状态动画；完整内发光、系统多通话/来电界面、折叠屏和横屏布局未逐项还原。原 APK 签名未验证。本轮结论以静态证据和构建测试为限。
