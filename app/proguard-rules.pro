-dontwarn org.immutables.value.Value$Default
-dontwarn org.immutables.value.Value$Immutable
-dontwarn org.immutables.value.Value$Style$BuilderVisibility
-dontwarn org.immutables.value.Value$Style$ImplementationVisibility
-dontwarn org.immutables.value.Value$Style

-keep class com.yausername.youtubedl_android.** { *; }
-keep class org.immutables.** { *; }

# SMBJ + transitive deps use reflection (SLF4J binding, ASN.1, packet builders)
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class com.rapid7.client.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**

# androidx.security crypto / Tink internals
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**