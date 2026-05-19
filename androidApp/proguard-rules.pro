# Kotlin Metadata (required for KMP)
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# kotlinx.datetime
-keep class kotlinx.datetime.** { *; }

# tyme4kt (Chinese traditional calendar)
-keep class cn.tyme.** { *; }

# ViewModel (used by CalendarViewModel)
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep entry point composables referenced by string name
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
