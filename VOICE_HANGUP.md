# 本地语音挂断

演示通话进入“通话中”且获得麦克风权限后，`VoiceHangupDetector` 会启动本地关键词检测。识别到关键词时调用现有的挂断流程。通话保持、挂断或应用退到后台时停止监听。此功能只监听设备麦克风，不接入 Android Telecom。

可在设置的“语音挂断口令”中添加 2–10 个汉字的自定义口令，并勾选一个或多个启用。口令保存在本机；通话开始监听时，应用会将启用口令转换成模型所需的拼音音素行并写入应用私有目录。识别到任一启用口令都会调用同一挂断流程。多音字按拼音库的默认读音生成，个别多音字口令可能需要换一种说法。

依赖来源：

- [sherpa-onnx v1.13.8 Android AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)，SHA-256：`633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`。
- [中文/英文关键词模型](https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2)。APK 只打包 chunk-16 的 int8 encoder/joiner、decoder 与词表。

构建可验证代码和资源是否打包；识别准确度与麦克风行为需在真机上测试。
