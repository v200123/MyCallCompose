# 本地语音挂断

演示通话进入“通话中”且获得麦克风权限后，`VoiceHangupDetector` 会启动本地关键词检测。识别到关键词时调用现有的挂断流程。通话保持、挂断或应用退到后台时停止监听。此功能只监听设备麦克风，不接入 Android Telecom。

当前口令为“挂断电话”，定义在 `app/src/main/assets/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/keywords.txt`。更换口令时，按 [官方自定义关键词说明](https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html) 重新生成该文件的音素行；界面显示与识别目标会自动读取 `@` 后面的文字。

依赖来源：

- [sherpa-onnx v1.13.8 Android AAR](https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar)，SHA-256：`633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96`。
- [中文/英文关键词模型](https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2)。APK 只打包 chunk-16 的 int8 encoder/joiner、decoder 与词表。

构建可验证代码和资源是否打包；识别准确度与麦克风行为需在真机上测试。
