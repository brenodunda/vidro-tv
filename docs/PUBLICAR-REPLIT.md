# Rodar no Replit

No Replit, o projeto abre uma **página do Vidro** com capturas, recursos e o **download do APK**, que é compilado na própria nuvem do Replit.

> O Replit roda na nuvem e **não alcança a TV da sua casa**. Instalar na TV, fazer a limpeza e restaurar continuam sendo feitos pelo seu PC, com `tools/` (veja o [PASSO-A-PASSO](PASSO-A-PASSO.md)).

## Opção A: importar do GitHub (recomendado)

Antes, publique o projeto no GitHub ([PUBLICAR-GITHUB](PUBLICAR-GITHUB.md)).

1. Entre em https://replit.com, clique em **+ Create App** e depois em **Import from GitHub**.
2. Autorize o Replit no GitHub e escolha o repositório `vidro-tv`.
3. O Replit lê o `.replit` e o `replit.nix` e prepara sozinho:
   - Python 3.11;
   - JDK 17 (`javac`, `keytool`, `jarsigner`);
   - aapt2 (pacote Nix `aapt`).
4. Clique em **Run**. O `web/server.py` vai:
   1. baixar o `android.jar` (API 28) do Google na primeira vez;
   2. compilar o `dist/Vidro.apk`;
   3. servir a página na aba **Webview**. O status muda para **APK pronto** e o botão **Baixar Vidro.apk** fica ativo.

## Opção B: enviar o ZIP

1. Clique em **+ Create App**, escolha o modelo **Python** e dê o nome `vidro-tv`.
2. Na aba **Files**, use **⋮ › Upload folder** (ou arraste o conteúdo do ZIP) e substitua os arquivos do modelo.
3. Confira se os arquivos `.replit` e `replit.nix` estão na raiz. Ative *Show hidden files* para ver os dois.
4. Clique em **Run**.

## Publicar a página (Deploy)

1. Vá em **Deploy** e escolha **Autoscale** ou **Reserved VM**. O `.replit` já define o comando `python3 web/server.py`.
2. A página ganha um endereço `https://vidro-tv-<usuario>.replit.app`, com download do APK, capturas e links para a documentação.

## Rodar as ferramentas no Shell do Replit

Dá para usar sem TV, só para validar:

```bash
python -m pip install -r requirements.txt
python tools/debloat.py config/debloat-tcl-android9.txt --dry-run
python build/build.py
python build/build.py atalho-globoplay      # atalho do botão Globoplay -> YouTube
```

## Problemas comuns

| Sintoma | Solução |
|---|---|
| `aapt2 nao encontrado` | No Shell, rode `python build/fetch_toolchain.py`: ele baixa o aapt2 Linux do Google. Ou confira se `pkgs.aapt` está no `replit.nix` |
| `javac: command not found` | Confira `pkgs.jdk17_headless` no `replit.nix` e recarregue o Repl (*Stop* e depois *Run*) |
| Página diz "falha na compilação" | Leia o log na própria página; o erro aparece no fim |
| APK baixado não atualiza o Vidro instalado | A chave de assinatura é outra. Envie o seu `vidro.jks` para `build/keystore/` (está no `.gitignore`, não vai para o Git) e, se a senha ou o alias não forem os padrões, crie os Secrets `VIDRO_KEYSTORE_PASS` e `VIDRO_KEY_ALIAS`. Ou desinstale o Vidro antes |
