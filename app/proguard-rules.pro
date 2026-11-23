# kotlinx.serialization: сохранить сериализаторы моделей API
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class kz.duken.mobile.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class kz.duken.mobile.**$$serializer { *; }
# Retrofit: интерфейс API читается через рефлексию
-keep,allowobfuscation interface kz.duken.mobile.data.api.ShopApi
-keepclassmembers,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
