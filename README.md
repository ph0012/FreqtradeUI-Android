# FreqtradeUI-Android

Aplicativo Android nativo (Kotlin + Jetpack Compose) inspirado no
[freqtrade/frequi](https://github.com/freqtrade/frequi), com interface própria e
conexão planejada ao Freqtrade por meio de um túnel SSH local.

> **Estado atual:** estrutura inicial funcional e compilável. A conexão SSH e a
> integração com a API **ainda não** foram implementadas. Os dados de saldo,
> lucro e operações são placeholders — nenhum valor simulado é exibido como real.

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Gradle (Kotlin DSL) com Gradle Wrapper versionado (8.13)
- Android Gradle Plugin 8.13.2
- AndroidX / Navigation Compose / Lifecycle
- minSdk 26, compileSdk/targetSdk 36

## Estrutura

```
app/src/main/java/com/freqtradeui/android/
├── MainActivity.kt            # Único ponto de entrada (Activity)
├── navigation/AppNavigation.kt# Navegação de nível superior (Dashboard/Trades/Settings)
├── ui/
│   ├── dashboard/             # DashboardScreen + DashboardViewModel
│   ├── trades/                # TradesScreen
│   ├── settings/              # SettingsScreen (placeholder)
│   ├── components/            # Componentes reutilizáveis (cards, indicadores)
│   └── theme/                 # Cores, tipografia e tema Material 3
├── data/
│   ├── api/                   # (futuro) Retrofit/OkHttp + JWT
│   ├── models/                # Modelos de dados (ConnectionStatus, Balance, etc.)
│   └── repository/            # (futuro) repositórios
├── ssh/                       # (futuro) gerenciador do túnel SSH
└── security/                  # (futuro) armazenamento seguro de credenciais
```

Os pacotes `data/api`, `data/repository`, `ssh` e `security` existem para
receber as próximas etapas sem refatoração da arquitetura.

## Build

Requisitos: JDK 21 e Android SDK em `C:\Android\Sdk` (configurado em
`local.properties`, que **não** é versionado).

```bash
# Debug
./gradlew assembleDebug

# Testes de unidade
./gradlew testDebugUnitTest

# Instalar em dispositivo/emulador conectado
./gradlew installDebug
```

O APK de debug é gerado em:
`app/build/outputs/apk/debug/app-debug.apk`

## Segurança

- Apenas a permissão `INTERNET` é declarada.
- Nenhuma credencial, chave privada ou senha está presente no código.
- Nenhum endpoint público fictício.
- Tráfego HTTP sem criptografia **não** está habilitado globalmente; as exceções
  de rede para o loopback do túnel serão definidas na etapa de conexão.

## Próxima etapa

Implementar a conexão SSH por chave privada (gerenciador em `ssh/`, com
encaminhamento da porta local `18080` → `127.0.0.1:8080` na VPS, verificação da
chave de host e armazenamento seguro de credenciais em `security/`).
