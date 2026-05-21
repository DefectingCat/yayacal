# Baseline Profiles 保留规则：确保方法名不被 R8 混淆，使 profile 规则匹配正确
-keepattributes SourceFile,LineNumberTable

# 保留 YaYa 业务类方法名（profile 中引用的类）
-keepclassmembers class plus.rua.project.ui.DayCellKt {
    public static void DayCell(...);
}
-keepclassmembers class plus.rua.project.LunarCache {
    public static plus.rua.project.DayCellInfo getOrCompute(kotlinx.datetime.LocalDate);
    public static java.lang.String formatLunarDate(kotlinx.datetime.LocalDate);
}
-keepclassmembers class plus.rua.project.DayCellInfo {
    public java.lang.String getAnnotationText();
    public boolean getIsAnnotationHighlight();
    public java.lang.String getHolidayBadge();
    public java.lang.String getLunarMonthName();
}
