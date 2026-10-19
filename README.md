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
| Clocks fixos | Prende CPU e GPU num ponto estável, reduzindo a oscilação entre quadros |
| Não perturbe | Silencia notificações e chamadas durante o jogo |
| Override térmico | Impede o sistema de reduzir os clocks por temperatura |
| Jogos recentes | Guarda os jogos escolhidos e mostra o ícone de cada um |
| Abrir com o perfil pronto | Tocar no jogo aplica o perfil e abre ele na sequência |
| Modo automático | Ativa o perfil sozinho quando um jogo abre |
| Medição | Mostra temperatura, clock da CPU, fluidez e nível de throttling |
| Status do Shizuku | Indica se o Shizuku está ligado e se o app está conectado a ele |
| Overlay no jogo | Faixa com o nome do jogo no topo e FPS no canto inferior esquerdo |
| Liberar memória | Encerra processos em segundo plano, sob demanda |
| Restaurar padrões | Desfaz todas as alterações e volta o aparelho ao normal |

## Requisitos

- Android 11 (API 30) ou superior
- [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
  instalado e ativo
- Permissão "sobrepor outros apps" para o overlay

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

## O que esperar de ganho

O teto de ganho é o hardware. Nenhum app aumenta o poder do chip. Na prática:

- **Escala de renderização** é o único ajuste que aumenta FPS de forma clara,
  porque reduz o trabalho da GPU. É o que o Game Booster faz por baixo.
- **Modo performance e clocks fixos** reduzem a *oscilação* entre quadros. O FPS
  médio muda pouco, mas a sensação de fluidez melhora, porque somem os picos de
  travada.
- **Não perturbe** só evita interrupções. Não tem efeito em FPS.
- **Override térmico** segura os clocks por mais tempo, mas esquenta mais. Em
  sessão longa, o calor acumulado pode acabar piorando o desempenho.

Se o jogo já está limitado pela GPU em um aparelho fraco, o downscale é o que
resolve. Se o aparelho é forte e o jogo está travando por *jank*, os clocks
fixos ajudam. Fora disso, o ganho é pequeno.

## O que o app não faz

- Não promete ganho de 100%.
- Não reserva RAM exclusiva para o jogo. Isso não existe no Linux.
- Não mexe em governor de CPU, cgroups ou driver de GPU: exige root de verdade.
- Não mata processos em segundo plano ao ativar o perfil. Ver a nota abaixo.

## Notas

- O overlay só aparece enquanto o jogo está em primeiro plano. Ao sair, ele
  desaparece sozinho.
- O FPS mostrado é o do jogo, lido do SurfaceFlinger. `dumpsys gfxinfo` não
  serve para jogos: ele só conta quadros desenhados pelo toolkit de UI, e jogos
  desenham via SurfaceView com OpenGL ES ou Vulkan.
- O perfil **não** roda `am kill-all` automaticamente. Liberar memória na
  abertura do jogo derruba serviços que o próprio jogo usa e força recarregar
  dados, o que causa travamento. O Android já rebaixa os processos em segundo
  plano quando um jogo assume a tela. O botão manual continua disponível para
  quem quer liberar memória entre sessões.
- O override térmico esquenta mais e pode reduzir a vida útil dos componentes.
- O Shizuku roda como o usuário `shell`. Alguns comandos podem ser recusados
  conforme o fabricante; o painel de atividade mostra qual falhou.

## Compilar

```bash
gradle :app:assembleDebug
```

O APK sai em `app/build/outputs/apk/debug/`.

Sem o Android Studio local, use o workflow do GitHub Actions: faça o push e
baixe o artefato **TurboKn-debug-apk** na aba Actions.
