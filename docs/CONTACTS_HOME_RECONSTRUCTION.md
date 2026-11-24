# 电话本首页还原记录

## 正确目标与回滚

目标是 `电话本 2026-10-22 22-51-59 423.apk`，包名 `com.android.contacts`，版本 `16.80.15`（160800015）。SHA-256：`2bd56933a967b1fbfbdb0c0cf741b6746e7ad36b79329d072b4fe27a96c160c8`。

上一轮错误目标的改动已经备份后回滚；本轮从干净基线重新实现。备份位于 `/Users/liuchang/Documents/AndroidProject/fanbianyi/analysis/wrong-target-backup-20261022-2252`。

## 实现内容

- 通话首页：标题、全部/未接切换、拨号盘收起/展开、双卡按钮、底部导航、记录行布局和相对日期。
- 记录按时间排序，仅合并相邻同号码记录；未接筛选根据每组最新记录判断。支持长按选择、全选和删除所选本地记录。
- 输入号码按包含关系匹配，支持中间数字匹配及绿色高亮；归属地放右侧，支持换行。新增联系人和保存至已有联系人写入本应用本地联系人。
- 联系人首页：数量、名片提示、我的名片/群组入口、头像、字母索引、新建入口及搜索。联系人和提示状态本地持久化。
- 通话过程沿用项目已有模拟流程。

## 分析依据

REA 静态分析与已连接 RMX5200 真机观察结合。真机为 1440×3136、640 dpi，首页按 360 dp 宽布局。分析文件、截图与证据包位于 `/Users/liuchang/Documents/AndroidProject/fanbianyi/analysis/contacts-16.80.15`。

关键静态证据：

| 内容 | REA Evidence ID |
| --- | --- |
| APK 包信息 | ev_7a64f43fb5074bb8edb21a8b3048f6181899a45e0730076af69c15b1959c6bd2 |
| DialtactsFragment | ev_ab90a766c260853b7accb7ca8022e10f7bba6f375e3f6bbf033289461a4e4b89 |
| 输入变化处理 | ev_bebaa4ca5bc20189a069d21b1c29aed2d5a5f47e2917dedd46a777c73d7c2459 |
| 相邻通话分组 | ev_f9a61f2eb5421dfb075f55cf399fd6c85e0e885125520789df9ed69058007761 |
| 原版号码中间匹配实拍 | ev_4ecccc51174be65bd13ef1dbaaf69284914dc13414192e208b563d81f3716dd0 |

完整 `evidence.json` 是本次 REA 会话账本，可能同时含有旧目标的历史证据；判断正确目标时以上述 APK 摘要、版本和证据索引为准。静态代码仅用于推导行为，实拍观察用于确认当前设备表现。

## 验证结果

- `:app:assembleDebug` 通过。
- `:app:testDebugUnitTest` 通过，共 9 项，0 失败。覆盖相邻分组、未接过滤、中间号码匹配、日期和归属地回退等。
- 最新独立预览包安装并启动成功；包名 `com.lastcoffee.top.oppo_call_compose.preview`，名称“电话（预览）”。安装证据 `ev_2b694ff5490ebbbce4d6483cf864d8ad22dbeea1bdf5bfd3c784d5f2f4789d8d`，启动证据 `ev_cf67d86277f2049b7c31627305f800087a52df9e6f1f8e6f20f3df8aedbd9c8c`。
- 真机输入 `136`，确认匹配 `19136066659`；联系人页切换、我的名片和我的群组入口显示检查通过。截图为分析目录下 `preview-checks/input-136.png`、`preview-checks/contacts.png`。
- 预览包使用独立应用数据。系统电话本和用户真实联系人、通话记录未改动，未拨打真实电话。

## 还原范围与差异

本轮还原通话与联系人首页的主要样式和交互，并非整个系统电话本。名片、群组和菜单的二级页面为本地简化实现；营业厅保留项目原页面。预览使用本地示例联系人，未导入设备通讯录。现有记录没有 SIM 槽字段，双卡时记录旁的 SIM 图标使用默认卡 1；双卡按钮均进入已有模拟通话流程。号码输入暂不包含原版完整的光标编辑和系统智能检索能力。

原项目包名和签名配置未改变。由于设备已有应用签名不同，真机验证使用临时副本中的独立预览包名；仓库中保留正常构建配置。

## 2026-11-21 样式校正

本次再次核对用户指定的同一 APK（摘要与本文开头一致），依据原版资源、静态方法和此前同版真机截图修改，而不是重新设计界面。

- 号码区改为原生可编辑控件，保留光标、选区、长按粘贴、纯号码复制和剪切；拨号按键在选区处输入、删除。对齐原版 600 字重、30dp 起始字号和 100 字符上限。移除额外常驻粘贴图标。两行间距保留用户本次明确要求的增加。
- 视频入口换成原版 `res/drawable/pb_ic_video_call.xml` 的空心图标；录音入口换成 `pb_ic_voicemail_resume` / `pause` 的 28dp 原版矢量。搜索联系人结果恢复全宽分隔线。
- 录音播放器按 `detail_call_history_item.xml` 的位置、12sp medium 时间与文件入口、150ms 展开动画校正；实际文件可用时才显示播放键。圆形进度滑块替换 Material 默认控件。原版 COUISeekBar 最小测量高度为 36dp；其默认轨道资源为 20dp，与用户实拍细线不同，当前细轨道依照用户图片复现，动态主题效果仍需同机运行核对。
- “查看文件”通过 FileProvider 和标准 `ACTION_VIEW` 打开本地音频。原版调用 OPPO 私有 `oplus.intent.action.BROWSE_FILE`；此处使用可用的标准 Android 替代。添加和更换录音仍通过点击通话记录进入管理页。

关键证据：包信息 `ev_ae75d2febe41cefa8dd47676e10c87c12f075d9880a550f2e51c81368df1bcdf`；号码触摸/菜单 `ev_30bf4966baca4ed8625467eecc7ac18dffc2565bdb91bf38ba8c9579d7a51ab9`、`ev_43b0ab3b06505765fd758236b80bd16ff4e593c0be76f251c995a0c0a31487ee`；录音按键绑定 `ev_cc1f7c113fea22a8ecd5247eb6e1cc664270266d36ea4f14e5acc81d1caa72c1`；播放器展开 `ev_e723c4930c81e1f9f34ebaf4d8f29309569a5cf077f2cf3e545da9c621abdd6b`；文件入口 `ev_cdec68f34b1a7d7bf9687bfd58e2c54e82a2a46dc0b5c3e765e5b7888f5ff620`。

本次 `adb install -r` 返回 `no devices/emulators found`；未安装或启动原 APK，也未进行本次真机截图对照。上文历史运行结果不能视为本次修改的验证结果。该电话本 APK 不能单独证明另一个系统通话组件的运行样式。

本轮验证：`:app:testDebugUnitTest` 59 项通过，0 失败；`:app:assembleDebug`、`:app:assembleDebugAndroidTest` 通过。新增检查覆盖光标插入、反向选区替换、退格、格式化偏移以及 100 字符上限。设备测试包仅构建，未运行。

本轮证据账本保存在同版分析目录下的 `style-alignment-20261121.evidence.json`、`dialer-style-20261121.evidence.json`、`recording-style-20261121.evidence.json`；历史字号方法证据仍位于既有 `evidence.json`。
