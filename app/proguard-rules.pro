-keep class com.baidu.** { *; }
-keep class vi.com.** { *; }
-keep class com.baidu.vi.** { *; }
-dontwarn com.baidu.**

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-optimizationpasses 7
-useuniqueclassmembernames
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
