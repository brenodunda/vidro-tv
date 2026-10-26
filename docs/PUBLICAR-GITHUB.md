# Publicar no GitHub

## 1. Antes de publicar: o que NÃO pode subir

O `.gitignore` já bloqueia estes itens, mas confira mesmo assim:

| Arquivo/pasta | Por quê |
|---|---|
| `tools/.adbkey`, `tools/.adbkey.pub` | É a chave que a sua TV autorizou. Com ela, alguém na sua rede teria acesso à TV |
| `build/keystore/*.jks` | É a chave de assinatura do APK. Quem tiver o arquivo pode publicar "atualizações" do seu app |
| `backups/`, `state/`, `shots/` | Têm número de série, MAC, ESN da Netflix e a lista de apps da sua casa |
| `build/toolchain/android.jar`, `aapt2*` | A licença do Android SDK não permite redistribuir; o `fetch_toolchain.py` baixa esses arquivos |

Confira com `git status` depois do passo 3: nenhum desses itens pode aparecer na lista.

## 2. Criar o repositório no site

1. Entre em https://github.com/new.
2. Preencha:
   - **Repository name:** `vidro-tv` (ou o nome que preferir);
   - **Description:** `Launcher "vidro líquido" para Android TV + kit ADB em Python`;
   - visibilidade **Public** ou **Private**.
3. **Não** marque *Add a README*, *.gitignore* nem *license*, porque o projeto já tem os três.
4. Clique em **Create repository** e copie a URL (por exemplo `https://github.com/SEU-USUARIO/vidro-tv.git`).

## 3. Enviar os arquivos

### Opção A: pelo terminal (Git instalado: https://git-scm.com/download/win)

Na pasta do projeto:

```bash
git init
git config user.name  "Seu Nome"
git config user.email "seu-email-publico@exemplo.com"   # ou o e-mail "noreply" do GitHub
git add .
git status                       # confira a lista (seção 1)
git commit -m "Vidro 1.2: launcher vidro líquido + kit ADB"
git branch -M main
git remote add origin https://github.com/SEU-USUARIO/vidro-tv.git
git push -u origin main
```

> **Privacidade do e-mail:** em https://github.com/settings/emails, ative *Keep my email addresses private* e use o endereço `…@users.noreply.github.com` no `git config user.email`. Assim o seu e-mail pessoal ou do trabalho não aparece nos commits.

### Opção B: pelo site, sem terminal

Na página do repositório vazio, clique em **uploading an existing file** e arraste o **conteúdo** da pasta, não a pasta em si.
O upload pelo site **ignora o `.gitignore`**, então **não arraste** `tools/.adbkey*`, `build/keystore/`, `backups/`, `state/` nem `shots/`.
A pasta `.github` às vezes fica de fora no arrastar; se acontecer, crie `.github/workflows/build.yml` pelo botão *Add file › Create new file* e cole o conteúdo.

### Opção C: GitHub Desktop

Use *File › Add local repository*, depois *Publish repository*.

## 4. Compilação automática (GitHub Actions)

O arquivo `.github/workflows/build.yml` compila o APK a cada `push`:

1. Abra a aba **Actions** do repositório e entre no workflow **Compilar APK**.
2. Ao terminar, baixe o APK em **Artifacts › Vidro-apk**.

### Usar a SUA chave no CI (para o APK atualizar o app já instalado)

No PC, gere a versão base64 da chave:

```bash
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("build\keystore\vidro.jks")) | Set-Clipboard
# Linux/macOS
base64 -w0 build/keystore/vidro.jks
```

Depois, no GitHub, vá em **Settings › Secrets and variables › Actions › New repository secret** e crie:

| Nome | Valor |
|---|---|
| `KEYSTORE_BASE64` | o texto copiado |
| `KEYSTORE_PASS` | a senha da chave (padrão `vidro-local`) |
| `KEY_ALIAS` | o alias (padrão `vidro`) |

Sem esses secrets, o CI cria uma chave temporária. O APK funciona numa instalação nova, mas não atualiza um Vidro já instalado.

## 5. Publicar uma versão (Release)

```bash
git tag v1.2
git push origin v1.2
```

O workflow cria a **Release v1.2** com o `Vidro.apk` anexado.
O atalho Globoplay **não** vai para a Release de propósito (veja o [NOTICE](../NOTICE.md)): cada pessoa compila o seu.

## 6. Deixar o repositório apresentável

- Na página inicial, use a engrenagem ao lado de **About** para preencher a descrição e os *Topics*: `android-tv`, `launcher`, `glassmorphism`, `adb`, `python`, `tcl`.
- Em **Settings › Social preview**, envie `docs/img/aurora.jpg`.
- Em **Settings › General › Features**, ative *Issues* e *Discussions* se quiser receber retorno.

## 7. Atualizar depois

```bash
git add .
git commit -m "descrição da mudança"
git push
```
