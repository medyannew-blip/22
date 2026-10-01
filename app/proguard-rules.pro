# Tasker
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.tasker.app.**$$serializer { *; }
-keepclassmembers class com.tasker.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.tasker.app.data.** { *; }
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
