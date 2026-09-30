# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Compose runtime
-dontwarn androidx.compose.**

# Keep BLE device types enum for reflection-free serialization
-keep class io.github.wxmyyds.coldfront.domain.CoolerDeviceType { *; }
-keep class io.github.wxmyyds.coldfront.domain.LightEffect { *; }
