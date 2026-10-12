# FreqtradeUI-Android

Aplicativo Android nativo (Kotlin + Jetpack Compose) inspirado no
[freqtrade/frequi](https://github.com/freqtrade/frequi), com interface própria e
conexão ao Freqtrade por meio de um túnel SSH local.

> **Estado atual:** o **túnel SSH está implementado** — perfis de conexão
> múltiplos, autenticação por chave privada, verificação de chave de host (TOFU),
> reconexão automática e serviço em primeiro plano.
>
> A **API REST do Freqtrade ainda não foi integrada**. Saldo, lucro/prejuízo e
> operações abertas continuam placeholders explícitos: nenhum valor simulado é
> apresentado como real, e o indicador "API" permanece desconectado de verdade.

## Stack

- Kotlin 2.2.10
- Jetpack Compose + Material 3 (BOM 2025.09.01)
- Gradle (Kotlin DSL) com Wrapper versionado em `gradle/wrapper` (8.13)
- Android Gradle Plugin 8.13.2
- AndroidX: Navigation Compose, Lifecycle, DataStore Preferences 1.1.7
- `com.github.mwiede:jsch` 2.28.8 — fork mantido do JSch, com suporte a chaves
  OpenSSH/ed25519 no Android
- `androidx.security:security-crypto` 1.1.0 — `EncryptedSharedPreferences`
  (Tink + Android Keystore)
- Kotlinx Coroutines 1.10.2
- minSdk 26, compileSdk/targetSdk 36

## Estrutura

```
app/src/main/java/com/freqtradeui/android/
├── MainActivity.kt                 # Único ponto de entrada (Activity)
├── FreqtradeApplication.kt         # Composição de dependências (AppContainer)
├── navigation/AppNavigation.kt     # NavHost: perfis, painel e editor
├── ui/
│   ├── profiles/                   # Lista, boas-vindas, editor de perfis e ViewModel
│   ├── dashboard/                  # Painel do perfil conectado + ViewModel
│   ├── trades/                     # TradesScreen (reservado para o painel REST)
│   ├── components/                 # Cards, indicadores, header padrão, chip de estado
│   └── theme/                      # Cores, tipografia e tema Material 3
├── data/
│   ├── models/                     # Perfil, estado/estados, máquina, backoff, classificador
│   ├── repository/                 # Repositório de perfis + persistência (DataStore)
│   └── api/                        # (futuro) Retrofit/OkHttp + JWT
├── ssh/                            # SshTunnelManager + SshConnectionService
└── security/                       # Cofre de segredos, chaves de host, fingerprint
```

O pacote `data/api` existe para receber a integração REST sem refatoração da
arquitetura.

## Arquitetura da conexão

Três camadas, com fronteiras testáveis:

- **`ConnectionStateMachine`** — máquina de estados pura e explícita
  (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `RECONNECTING`, `ERROR`,
  `STOPPING`). O redutor é determinístico e não faz I/O, então cada transição é
  verificada em teste de unidade. O gerenciador apenas executa o efeito pedido
  (`OpenTunnel`, `ScheduleRetry`, `CloseTunnel`, `StopService`).
- **`ReconnectPolicy`** — backoff exponencial (2 s → 60 s) com teto e **jitter**
  de ±20%, para evitar reconexões sincronizadas. Falhas permanentes
  (autenticação, configuração, chave de host alterada) **não** geram nova
  tentativa; falhas transitórias (rede, timeout, sessão caída) sim.
- **`SshTunnelManager`** — abre a sessão JSch, encaminha a porta local para a API
  na VPS, mantém keepalive SSH e verifica a saúde do encaminhamento com um teste
  TCP periódico na porta local (sessão aberta não é sinônimo de túnel vivo).
- **`SshConnectionService`** — `Foreground Service` do tipo `dataSync` com
  `START_NOT_STICKY`. É iniciado **somente por ação explícita do usuário**, exibe
  notificação persistente com o estado real e permite desconectar por ela. O
  túnel sobrevive à destruição da Activity.

### Verificação da chave de host (TOFU)

1. Na primeira conexão, o app recusa a chave desconhecida e apresenta a
   impressão digital **SHA-256** para conferência fora de banda.
2. A aprovação é explícita; a chave só é gravada depois dela.
3. Nas conexões seguintes, uma chave **diferente** da aprovada é bloqueada sem
   oferecer aprovação automática (hipótese de man-in-the-middle).
4. A verificação **nunca** é desabilitada e uma nova chave não é aceita
   silenciosamente depois de uma falha.

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

### Permissões declaradas

| Permissão | Motivo |
|---|---|
| `INTERNET` | túnel SSH e chamadas à API via loopback |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | manter o túnel ativo fora da interface |
| `POST_NOTIFICATIONS` | notificação persistente do serviço (Android 13+) |
| `ACCESS_NETWORK_STATE` | antecipar a reconexão quando a rede cai |

Nenhuma permissão privilegiada desnecessária. `RECEIVE_BOOT_COMPLETED` é
**deliberadamente ausente**: reconectar sozinho após reiniciar exigiria iniciar
um serviço em background sem interação do usuário — conexão silenciosa que o app
não faz.

### Segredos

- A chave privada SSH e a frase secreta ficam em `EncryptedSharedPreferences`
  (Tink com chave-mestre protegida pelo **Android Keystore**), indexadas pelo
  `id` de cada perfil.
- Os dados **não sensíveis** (nome, host, portas) ficam em DataStore
  Preferences, em arquivo separado. Um backup ou despejo da configuração de
  perfis não expõe material de autenticação.
- Nenhuma credencial aparece em log — nem em caso de erro. O log registra
  apenas host/porta e a classe da exceção.
- Nenhuma credencial, chave ou senha está no código ou no repositório.
- Nenhum endpoint público fictício, e tráfego HTTP sem criptografia **não** está
  habilitado globalmente.

**Limitações do armazenamento:** a proteção vale contra acesso ao sistema de
arquivos (outro app, backup, extração via ADB com dispositivo bloqueado). Código
malicioso rodando como o mesmo usuário, ou um aparelho com root, pode conseguir
que o Keystore descriptografe. Por isso a chave privada continua sendo segredo do
usuário e o app nunca a exibe na tela — apenas o nome do arquivo de origem.

## Limitações do Android para conexões persistentes

O túnel depende de um serviço em primeiro plano, e o sistema **pode** interrompê-lo.
Isso não é uma falha do app, e sim o comportamento da plataforma:

- **Economia de bateria e Doze**: o Android restringe serviços em segundo plano.
  Otimizações agressivas podem congelar ou encerrar o processo.
- **Fabricantes**: várias ROMs (MIUI, EMUI, One UI, OxygenOS) aplicam regras
  próprias de "limpeza" e matam serviços em segundo plano mesmo com o app em
  primeiro plano. Em aparelhos Xiaomi, é preciso liberar o app em
  *Autostart* / *Sem restrições de bateria*.
- **Encerramento forçado**: se o usuário forçar a parada do app nas
  configurações do Android, **não há** como reiniciar o serviço automaticamente
  — o sistema bloqueia. É preciso abrir o app e conectar de novo.
- **Remover dos recentes**: com `stopWithTask="false"`, o serviço continua; ainda
  assim o fabricante pode encerrá-lo.
- **Reboot do aparelho**: o app não reconecta sozinho (sem
  `RECEIVE_BOOT_COMPLETED`). Ao reabrir, o estado é lido do serviço: nada é
  apresentado como conectado se o túnel não estiver ativo.
- **Morte do processo por memória**: `START_NOT_STICKY` é intencional — o
  serviço não tenta reconectar sem ação do usuário e sem a credencial em memória.

Se o serviço for encerrado pelo sistema, reabrir o app mostra o estado real
(desconectado) e permite reconectar. O app **nunca** afirma que uma conexão está
ativa quando o túnel caiu.

## Testes

```bash
./gradlew testDebugUnitTest
```

48 testes de unidade na JVM, sem emulador:

- `ConnectionStateMachineTest` — transições e efeitos, incluindo as garantias de
  segurança (desconexão nunca vira reconexão; falha permanente não retenta; chave
  de host alterada bloqueia)
- `ReconnectPolicyTest` — progressão do backoff, teto, jitter e limites
- `ConnectionErrorClassifierTest` — transitório vs. permanente
- `ConnectionProfileTest` — validação de perfis, regras de seleção/troca e
  persistência (identificador estável, exclusão que não deixa credencial órfã)
- `DashboardUiStateTest` — estado inicial desconectado e fingerprint SHA-256

## Próxima etapa

Integração com a API REST do Freqtrade **sobre o túnel já estabelecido**:
Retrofit + OkHttp apontando para o loopback local, autenticação por token JWT,
repositório e ViewModel para popular saldo, lucro/prejuízo e operações abertas —
além da exceção `usesCleartextTraffic` restrita a `127.0.0.1`. O destino
`TradesScreen` volta à navegação nessa etapa.

