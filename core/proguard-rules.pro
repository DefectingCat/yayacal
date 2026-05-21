# Baseline Profiles 保留规则：确保方法名不被 R8 混淆，使 profile 规则匹配正确
-keepattributes SourceFile,LineNumberTable

# ========== 启动热点路径保留 ==========

# DayCell — 启动最热点
-keepclassmembers class plus.rua.project.ui.DayCellKt {
    public static void DayCell(...);
}

# LunarCache — 日期计算缓存
-keepclassmembers class plus.rua.project.LunarCache {
    public static plus.rua.project.DayCellInfo getOrCompute(kotlinx.datetime.LocalDate);
    public static java.lang.String formatLunarDate(kotlinx.datetime.LocalDate);
    public static void precompute(...);
}

# DayCellInfo 数据类
-keepclassmembers class plus.rua.project.DayCellInfo {
    public java.lang.String getAnnotationText();
    public boolean getIsAnnotationHighlight();
    public java.lang.String getHolidayBadge();
    public java.lang.String getLunarMonthName();
}

# CalendarMonthPage
-keepclassmembers class plus.rua.project.ui.CalendarMonthPageKt {
    public static void CalendarMonthPage(...);
    public static java.util.List generateMonthDays(...);
}

# ========== 第三方库保留 ==========
-keep class kotlinx.datetime.** { *; }
-keep class cn.tyme.** { *; }
