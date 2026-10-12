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
