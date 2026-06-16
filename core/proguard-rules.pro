# Baseline Profile / Startup Profile 保留规则：确保方法名不被 R8 混淆，使 profile 规则匹配正确
-keepattributes SourceFile,LineNumberTable

# ========== 第三方库保留 ==========
-keep class kotlinx.datetime.** { *; }
-keep class cn.tyme.** { *; }
