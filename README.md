# TurboKn

Aplicativo Android que aplica perfis de desempenho em jogos usando o
[Shizuku](https://shizuku.rikka.app/). Não requer root, não modifica o sistema e
não quebra o Play Integrity.

Visual em preto, branco e cinza, com clima de samurai na neve.

## O que ele faz

| Recurso | Comando executado | Efeito real |
|---|---|---|
| Modo performance | `cmd game mode performance <pkg>` | Mecanismo oficial de prioridade do Android |
| Escala de renderização | `cmd game set --downscale <ratio> <pkg>` | Reduz a resolução interna. **É o que aumenta FPS de verdade** |
| Limite de FPS | `cmd game set --fps <n> <pkg>` | Estabiliza a taxa de quadros |
| Liberar memória | `am kill-all` | Reduz travadas, não sobe FPS médio |
| Override térmico | `cmd thermalservice override-status <n>` | Segura os clocks, mas esquenta mais |
| Detecção automática | `AccessibilityService` | Ativa o perfil quando um jogo abre |

Todos os comandos foram verificados contra o
[`GameManagerShellCommand`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/app/GameManagerShellCommand.java)
do AOSP.

## O que ele não faz

- Não promete ganho de 100%. O limite é o hardware.
- Não clona o Game Booster da Samsung.
- Não burla SafetyNet/Play Integrity — não precisa, porque não usa root.
- Não altera governor de CPU, cgroups ou GPU: isso exige root de verdade e o
  Shizuku roda como UID `shell`.

## Requisitos

- Android 11 (API 30) ou superior
- [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) instalado e ativo

## Como usar

1. Instale o APK.
2. Ative o Shizuku (via depuração sem fio ou root) e conceda a permissão ao TurboKn.
3. Escolha o jogo na lista.
4. Ajuste a escala de renderização, se quiser ganho de FPS.
5. Toque em **ATIVAR**.

Para o modo automático, ligue a chave **Ativar ao entrar em jogos** e habilite o
serviço de acessibilidade quando solicitado. Se o Shizuku estiver desligado
quando um jogo abrir, o app avisa por notificação — única notificação que ele
exibe.

## Arquitetura

```
app/src/main/
├── aidl/com/turbokn/app/ITurboKnService.aidl   interface do serviço privilegiado
├── java/com/turbokn/app/
│   ├── shizuku/
│   │   ├── TurboKnUserService.kt   roda como UID shell, executa comandos
│   │   └── ShizukuShell.kt         ciclo de vida e ponto único de execução
│   ├── core/
│   │   ├── GameActions.kt          comandos mapeados
│   │   ├── ProfileEngine.kt        aplica e reverte o perfil
│   │   ├── AutoModeController.kt   modo automático fora da UI
│   │   ├── GameWatcherService.kt   detecção de app em primeiro plano
│   │   └── ShizukuNotifier.kt      aviso de Shizuku ausente
│   ├── data/
│   │   ├── InstalledApps.kt        lista de apps instalados
│   │   └── SettingsStore.kt        perfil persistido
│   ├── ui/                         Compose: tela, tema e ViewModel
│   ├── MainActivity.kt
│   └── TurboKnApplication.kt
└── res/                            tema, ícone e configuração de acessibilidade
```

O app usa um **User Service** do Shizuku (AIDL), e não o `newProcess`, porque
este último é privado na API 13 e será removido na API 14.

## Compilar

Com o Gradle instalado:

```bash
gradle :app:assembleDebug
```

Ou gere o wrapper uma vez (`gradle wrapper`) e use `./gradlew :app:assembleDebug`.

O APK sai em `app/build/outputs/apk/debug/`.

Para compilar sem o Android Studio local, use o workflow do GitHub Actions:
faça o push e baixe o artefato **TurboKn-debug-apk** na aba Actions.

## Aviso

O override térmico força o aparelho a ignorar o throttling. Isso aumenta a
temperatura e pode reduzir a vida útil dos componentes. Use com critério.
