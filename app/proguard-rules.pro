# Regras do ProGuard/R8.
#
# O build de release ainda não possui dependências que exijam regras específicas
# (Retrofit/OkHttp/SSH serão adicionados nas próximas etapas). Mantenha este
# arquivo versionado para registrar as regras necessárias no futuro.

# Preserva anotações usadas por bibliotecas de reflexão.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Linhas de arquivo legíveis em stack traces de release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- JSch (túnel SSH) ---
# O JSch instancia algumas implementações (algoritmos de criptografia/assinatura)
# por reflexão e usa JNI em parte. Evita que o R8 remova ou renomeie essas classes,
# o que causaria falhas de conexão apenas em builds de release.
-dontwarn com.jcraft.jsch.**
-keep class com.jcraft.jsch.** { *; }
-keep interface com.jcraft.jsch.** { *; }

# --- Tink / androidx.security-crypto ---
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { *; }
