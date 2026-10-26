# Passo a passo: do zero até o Vidro na sua TV

Tempo estimado: de 30 a 60 minutos. Tudo é reversível com `tools/restore.py`.

## 0. O que você precisa

- **Android TV 7 ou mais novo** na mesma rede do PC (o projeto foi testado em Android 9).
- **PC** com Windows, Linux ou macOS e:
  - **Python 3.8+**: https://www.python.org/downloads/ (no Windows, marque *Add Python to PATH*);
  - **JDK 11+**, só para compilar: por exemplo o Temurin 17, em https://adoptium.net/.
- Este repositório: baixe o ZIP ou use `git clone`.

Abra um terminal na pasta do projeto. No Windows, use o PowerShell.

```bash
python -m venv .venv
# Windows:  .venv\Scripts\activate        Linux/macOS:  source .venv/bin/activate
python -m pip install -r requirements.txt
```

## 1. Liberar o ADB na TV

1. Na TV, vá em **Configurações › Preferências do dispositivo › Sobre** e clique **7 vezes** em **Compilação** (ou *Build*).
2. Volte e abra **Opções do desenvolvedor**. Ative **Depuração USB** e, se existir, também **Depuração de rede/ADB**.
3. **Reinicie a TV.** Em várias TCL a porta 5555 só abre depois de reiniciar.

Vídeo da tela de Opções do desenvolvedor numa TCL (14 s): [video/opcoes-desenvolvedor.mp4](video/opcoes-desenvolvedor.mp4)

## 2. Encontrar a TV na rede

```bash
python tools/discover.py
```

Procure o IP com a coluna 5555 marcada como **ABERTA**, ou o MAC igual ao que aparece em *Sobre › Status*. Daqui em diante, use esse IP. Para não repetir `--host` em todo comando, defina a variável:

```bash
# Windows PowerShell:  $env:TV_HOST="192.168.0.50"      Linux/macOS:  export TV_HOST=192.168.0.50
```

Teste a conexão. Na primeira vez a TV pergunta **"Permitir depuração USB?"**: marque *Sempre permitir* e aceite.

```bash
python tools/tv.py --screenshot shots/teste.png HOME
```

## 3. Backup (obrigatório)

```bash
python tools/backup.py
```

O backup vai para `backups/<data>/`: pacotes, desativados, `getprop`, configurações, apps de tela inicial e entradas.
Esses arquivos têm dados do aparelho (número de série, MAC). **Não publique.** A pasta já está no `.gitignore`.

## 4. Limpeza (opcional, em lotes)

1. Revise `config/debloat-tcl-android9.txt`. Ele foi feito para TCL com Android 9; adapte para a sua TV e descomente no **Lote 4** só o que você **não** usa.
2. Simule antes, sem mexer na TV:
   ```bash
   python tools/debloat.py config/debloat-tcl-android9.txt --dry-run
   ```
3. Aplique. O script pergunta antes de cada lote e tira uma captura entre eles:
   ```bash
   python tools/debloat.py config/debloat-tcl-android9.txt
   ```
4. **Reinicie a TV** e confira se nada foi reativado:
   ```bash
   python tools/tv.py --shell "pm list packages -d"
   ```

## 5. Compilar o Vidro

```bash
python build/fetch_toolchain.py   # uma vez: aapt2 + android.jar, baixados do Google
python build/build.py             # gera dist/Vidro.apk
```

Se você já tem o Android Studio, pode pular o `fetch_toolchain`: o build encontra o SDK sozinho.
**Guarde `build/keystore/vidro.jks`:** as atualizações do app precisam da mesma chave.

## 6. Instalar e testar (sem trocar a tela inicial ainda)

```bash
python tools/install.py
```

Abra **Vidro** na lista de apps da TV e navegue com o controle:

| Controle | O que faz |
|---|---|
| ◀ ▶ | Move entre os apps |
| ▲ ▼ | Uma linha por vez: barra do topo → favoritos → categorias → grade |
| OK | Abre o app, filtro ou menu |
| **Segurar OK** ou ≡ | Fixar, mover, remover ou ocultar o app |
| Voltar | Volta aos favoritos (nos favoritos, sai) |

Na pílula **Aparência** ficam os ajustes: papel de parede, transparência, efeitos, animações, modo do vidro e número de favoritos.

## 7. Tornar o Vidro a tela inicial

Faça isso só quando tiver gostado do resultado.

```bash
python tools/install.py --set-home                    # Vidro passa a ser a tela inicial
python tools/install.py --set-home --disable-stock    # e desativa o launcher original
```

O `--disable-stock` só desativa o launcher original **depois** de confirmar que o HOME já abre o Vidro.

## 8. Extras

- **Botão Globoplay → YouTube** (controles TCL): veja [BOTAO-GLOBOPLAY-YOUTUBE.md](BOTAO-GLOBOPLAY-YOUTUBE.md).
  ```bash
  python build/build.py atalho-globoplay
  python tools/install.py --atalho-globoplay
  ```
- **DNS que bloqueia anúncios (AdGuard):** use só se o seu roteador não filtrar nem redirecionar DNS.
  ```bash
  python tools/install.py --dns adguard        # desfazer: --dns off
  ```
- **Papel de parede seu** (de preferência JPG 1920×1080):
  ```bash
  python tools/tv.py --push foto.jpg /sdcard/Android/data/com.breno.tvhome/files/wallpaper.jpg HOME
  ```
  Depois escolha **Aparência › Papel de parede › Personalizado**.
- **Ordem dos favoritos pelo PC:** coloque um pacote por linha num `dock.txt` e envie:
  ```bash
  python tools/tv.py --push dock.txt /sdcard/Android/data/com.breno.tvhome/files/dock.txt HOME
  ```

## 9. Reiniciar e conferir

Depois de reiniciar a TV, ela deve abrir direto no Vidro. Confira:

```bash
python tools/tv.py --shell "dumpsys activity activities | grep mResumedActivity"
python tools/tv.py --shell "pm list packages -d"
```

## 10. Fechar o ADB (importante)

A porta 5555 é texto puro: com ela aberta, qualquer aparelho da rede pode pedir acesso.

1. Em **Opções do desenvolvedor**, toque em **Revogar autorizações de depuração USB** e desligue a **Depuração USB**.
2. Reinicie a TV e confira:
   ```bash
   python tools/adb_closed.py 192.168.0.50
   ```
   O resultado esperado é `OK: o ADB de rede esta fechado.`

## Desfazer tudo

```bash
python tools/restore.py                  # reativa pacotes, volta a tela original, DNS automático, remove o atalho
python tools/restore.py --remove-vidro   # e desinstala o Vidro
```

Sem PC, dá para reativar pela própria TV: **Configurações › Apps › Ver todos os apps › Apps do sistema › (app) › Ativar**.
