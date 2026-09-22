-keep class androidx.startup.InitializationProvider { *; }
-keep class androidx.startup.AppInitializer { *; }

# Keep Conscrypt in primary dex for early initialization
-keep class org.conscrypt.Conscrypt { *; }
-keep class org.conscrypt.OpenSSLProvider { *; }