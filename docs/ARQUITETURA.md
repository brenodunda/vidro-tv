# Arquitetura

## Restrições do aparelho de referência

| Item | Valor medido | Consequência |
|---|---|---|
| Android | 9 (API 28) | Sem `RenderEffect`/blur ao vivo (só existe a partir da API 31) e sem a necessidade de `<queries>` (só a partir da API 30) |
| CPU | ARM 32 bits (armv7l) | Java puro, sem bibliotecas nativas |
| Tela | 1280×720 @ 213 dpi | Layout em **unidades proporcionais**: 1 unidade = largura/960, então escala para 1080p e 4K |
| Memória/GPU | entrada | Sem vídeo, sem rede, sem bibliotecas pesadas (nem AndroidX) |

## Vidro fosco sem blur em tempo real

1. **`Backdrop`** carrega o papel de parede e recorta para o tamanho da tela (*center crop*). Em seguida, cria uma cópia **1/8** do tamanho, aumenta a saturação (1,35×), clareia um pouco e aplica 3 passadas de *box blur* na CPU. Como a imagem é pequena, isso leva só alguns milissegundos.
2. **`Frost`** é o `Drawable` de cada painel. Cada um:
   - pega a própria posição na janela (`getLocationInWindow`);
   - desenha a cópia desfocada com um `BitmapShader`, cuja matriz faz `escala ×8` e translada pela posição. O painel mostra exatamente o trecho do fundo que está atrás dele;
   - adiciona uma camada branca leitosa (a **Transparência**), um reflexo especular no topo, uma leve sombra embaixo e uma **borda iluminada** em gradiente (forte em cima e embaixo, como uma lente).
3. Quando o conteúdo rola, o `AnimatorUpdateListener` invalida os painéis para que o trecho amostrado acompanhe o movimento.
4. No **modo leve**, a amostragem é trocada por um preenchimento translúcido, ou seja, nenhum bitmap entra no desenho.

Como o fundo é estático, o resultado é visualmente igual ao blur ao vivo, com custo perto de zero.

## Seleção com profundidade

O bloco focado recebe:
- `scale 1.1` com `OvershootInterpolator`, o efeito de mola;
- `translationZ`, que gera a sombra do sistema;
- borda e reflexo mais fortes (`Frost.glow = 1`);
- um **brilho radial** atrás do bloco, na cor dominante do banner. Essa cor é a média ponderada pela saturação de uma miniatura de 24×14.

Uma **lente de vidro** desliza de um bloco para o outro. O nome do app aparece grande, sobre uma **luz ambiente**: um gradiente radial achatado em elipse com `scaleY`.

## Navegação determinística

Há quatro zonas: `TOP` (pílulas) → `DOCK` (favoritos) → `CHIPS` (categorias) → `GRID`. Cada toque ▲▼ move **exatamente uma** linha. As bordas não atravessam: ◀ no primeiro item não vai para a linha de cima. Ao descer, a coluna é limitada ao tamanho da linha seguinte. O último app focado fica gravado em `SharedPreferences`.

## Lista de apps

`queryIntentActivities` é feito em `LEANBACK_LAUNCHER` e em `LAUNCHER`. Um app que só tem `LAUNCHER` só entra se não for app de sistema, o que evita telas de configuração e stubs. As categorias vêm de `ApplicationInfo.category` e `FLAG_IS_GAME`, com alguns ajustes por nome de pacote conhecido.

## Entradas (HDMI/AV/TV)

`TvInputManager.getTvInputList()` lista as entradas e ignora `TYPE_OTHER` (apps que se registram como entrada, como Pluto e Play Filmes). Só um sintonizador de app de sistema é aceito. Para abrir uma entrada, o Vidro usa `ACTION_VIEW` com `TvContract.buildChannelUriForPassthroughInput(id)` no app de TV ao vivo do fabricante (`com.tcl.tv`, que pode ser trocado em `settings.txt`, chave `livetv=`).

## Arquivos de configuração (sem precisar recompilar)

Ficam em `/sdcard/Android/data/com.breno.tvhome/files/`:

| Arquivo | Conteúdo |
|---|---|
| `dock.txt` | um pacote por linha, na ordem dos favoritos |
| `hidden.txt` | pacotes ocultos da grade |
| `wallpaper.jpg` / `.png` | papel de parede "Personalizado" |
| `settings.txt` | `name=Breno` (saudação), `livetv=com.tcl.tv` |

Os ajustes visuais (papel de parede, transparência, efeitos, animações, modo, favoritos) ficam em `SharedPreferences`, gravados pelo menu Aparência.

## Build sem Gradle

`build/build.py` faz:
1. `aapt2 compile`;
2. `aapt2 link` com o `-I android.jar` da API 28;
3. `javac -source 8 -target 8`, sem lambdas, para manter compatibilidade com o `dx`;
4. `dx --min-sdk-version=24`;
5. dex dentro do APK;
6. `jarsigner`, com assinatura v1 (aceita do Android 7 ao 10 com `targetSdk 28`);
7. alinhamento em 4 bytes.

O APK final tem cerca de 1,1 MB, sendo 4 papéis de parede e 4 pesos de fonte.

## Kit ADB

Todas as ferramentas usam `tools/tvadb.py`, ADB em Python puro (`adb-shell`). Assim não é preciso o `adb.exe` nem o Android SDK, e fica contornado o bloqueio de "rede local" que alguns sistemas aplicam ao `adb` nativo. Princípios:
- **um comando `pm` por chamada**, porque comandos encadeados podem parar no primeiro;
- verificação pela lista **`pm list packages -d`**, não pela saída do comando;
- **`pm install -r`** em vez de `adb install`, que pode travar esperando confirmação na tela;
- envio de arquivo com **MD5** e uma alternativa em blocos **base64** se o *push* travar;
- capturas vazias (TV ao vivo, HDMI, Netflix) são detectadas e explicadas.
