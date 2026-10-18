# TurboKn

App Android de desempenho para jogos, no estilo de um "game booster". Aplica um
perfil ao jogo escolhido usando o [Shizuku](https://shizuku.rikka.app/). Sem
root, sem modificar o sistema e sem quebrar o Play Integrity.

Visual preto, branco e cinza, com tema de samurai na neve.

## Tema

- Preto tinta, cinza cinza, branco neve.
- Tipografia leve, com espaçamento largo no título.
- Cartões arredondados, ícones reais dos apps na lista de recentes.

## Funcionalidades

| Recurso | O que faz |
|---|---|
| Modo performance | Coloca o jogo no modo performance oficial do Android |
| Escala de renderização | Reduz a resolução interna do jogo. É o ajuste que mais aumenta FPS |
| Limite de FPS | Fixa a taxa de quadros do jogo |
| Liberar memória | Encerra processos em segundo plano, reduzindo travadas |
| Não perturbe | Silencia notificações e chamadas durante o jogo |
| Override térmico | Impede o sistema de reduzir os clocks por temperatura |
| Jogos recentes | Guarda os jogos escolhidos e mostra o ícone de cada um |
| Abrir com o perfil pronto | Tocar no jogo aplica o perfil e abre ele na sequência |
| Modo automático | Ativa o perfil sozinho quando um jogo abre |
| Medição | Mostra temperatura, clock da CPU, fluidez e nível de throttling |
| Status do Shizuku | Indica se o Shizuku está ligado e se o app está conectado a ele |
| Restaurar padrões | Desfaz todas as alterações e volta o aparelho ao normal |

## Requisitos

- Android 11 (API 30) ou superior
- [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
  instalado e ativo

## Como usar

1. Instale o APK.
2. Ative o Shizuku e conceda a permissão ao TurboKn.
3. Escolha o jogo. Ele passa a aparecer nos recentes.
4. Ajuste a escala de renderização e o limite de FPS, se quiser.
5. Toque em **ATIVAR**, ou toque no jogo nos recentes para abrir ele já com o
   perfil aplicado.

Para o modo automático, ligue a chave **Ativar ao entrar em jogos** e habilite o
serviço de acessibilidade quando solicitado. Se o Shizuku estiver desligado
quando um jogo abrir, o app avisa por notificação — a única notificação que ele
exibe.

## Notas

- O ganho de desempenho depende do aparelho. O limite é o hardware.
- O override térmico esquenta mais e pode reduzir a vida útil dos componentes.
- O Shizuku roda como o usuário `shell`. Ajustes de kernel, como governor de
  CPU, exigem root de verdade e não estão disponíveis.

## Compilar

```bash
gradle :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/`.

Sem o Android Studio local, use o workflow do GitHub Actions: faça o push e
baixe o artefato **TurboKn-debug-apk** na aba Actions.
