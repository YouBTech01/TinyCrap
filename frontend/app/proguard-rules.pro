# OkHttp, Coil and Compose ship their own consumer rules.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
