# TurboKn — notas do repositório

App Android (Kotlin + Jetpack Compose) que aplica um perfil de desempenho ao
jogo escolhido usando o Shizuku. `minSdk` 30, `targetSdk`/`compileSdk` 36.

## Estrutura

- `app/src/main/java/com/turbokn/app/shizuku/` — ponte privilegiada.
  `TurboKnUserService` roda como UID `shell`; `ShizukuShell` é o único ponto de
  execução de comandos e cuida do bind idempotente do user service.
- `app/src/main/java/com/turbokn/app/core/` — regras de negócio.
  `GameActions` mapeia cada comando shell; `ProfileEngine` aplica/reverte o
  perfil; `AutoModeController` + `GameWatcherService` fazem a detecção por
  acessibilidade; `MetricsReader` lê temperatura/clock/throttling/fluidez.
  O overlay vive em `GameOverlay` (as views), `OverlayCoordinator` (quando
  mostrar) e `FpsSampler` (o FPS do jogo), coordenados por `BoostState`.
- `app/src/main/java/com/turbokn/app/data/` — `SettingsStore` (DataStore) e
  `InstalledApps`.
- `app/src/main/java/com/turbokn/app/ui/` — Compose. `MainScreen` monta a tela,
  `Components.kt` tem os blocos reutilizáveis, `ShizukuCard.kt` e
  `GameSelector.kt` têm os blocos específicos.

## Compilar

```bash
gradle :app:assembleDebug     # APK em app/build/outputs/apk/debug/
gradle :app:lintDebug         # relatório em app/build/reports/
```

O CI (`.github/workflows/build.yml`) roda em `ubuntu-24.04` e publica o
artefato `TurboKn-debug-apk`. O runner é fixado de propósito.

## Armadilhas conhecidas

- **Interpolação de `$` em strings Kotlin.** Comandos shell com `$f` precisam
  de `${'$'}f`, senão o compilador tenta resolver `$f` como variável.
- **Cache de recursos.** Ao mover/renomear pastas `res/`, rode
  `--rerun-tasks`, senão o AAPT falha com "resource not found".
- **Ícones.** minSdk 30, então só existe `mipmap-anydpi/ic_launcher.xml` com o
  artwork na camada `background`. Não gerar PNGs legados.
- **Shizuku.** `newProcess` é privado na API 13; usar o user service via AIDL.
- **Overrides de sistema.** Sempre restaurar o estado anterior (`zen_mode` antes
  do DND, `cmd game reset` no fim) para não deixar o aparelho alterado.
- **Sem notificação persistente.** A detecção automática usa
  `AccessibilityService` justamente para evitar foreground service.
- **Overlay pertence ao jogo.** O HUD só pode ficar visível enquanto o pacote
  ativo está em primeiro plano. Quem decide isso é o `OverlayCoordinator`, que
  combina o evento da acessibilidade com um polling via Shizuku. Nunca chamar
  `GameOverlay.show` direto — sempre pelo coordenador, senão o HUD fica preso na
  tela ao sair do jogo.
- **FPS do overlay.** `FpsSampler` lê `dumpsys gfxinfo <pkg>` e divide a
  diferença de quadros pelo tempo. Medir os quadros do próprio app não serve: o
  overlay é desenhado em outro processo. A primeira leitura é só a linha de
  base, por isso o mostrador começa em `--`.
- **Ordem ao abrir o jogo.** O perfil é aplicado *antes* de lançar o app
  (`InstalledApps.launch`). Invertendo a ordem, o jogo sobe com a configuração
  padrão e o downscale só vale no próximo início.
- **Ambiente de build.** O JDK não sobrevive a um reset do sandbox; o Android
  SDK (`/workspace/android-sdk`) e o Gradle (`/workspace/tools`) sobrevivem.
  Se `java` sumir, reinstale com `apt-get install openjdk-21-jdk-headless`
  depois de um `apt-get update`.
