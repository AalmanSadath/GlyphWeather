# Nothing's Glyph Matrix SDK ships without R8 rules. It binds to the system Glyph service
# through AIDL-generated classes, so keep the whole SDK intact rather than guess which
# parts are reached only by name.
-keep class com.nothing.** { *; }
-dontwarn com.nothing.**
