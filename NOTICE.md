# Componentes de terceiros

| Componente | Onde | Licença |
|---|---|---|
| **Outfit** (fonte, Rodrigo Fuenzalida) | `app/assets/fonts/` — instâncias estáticas 200/300/500/600 geradas a partir da fonte variável do repositório google/fonts | SIL Open Font License 1.1 (`app/assets/fonts/OFL-Outfit.txt`) |
| **dx** (dexador do AOSP) | `build/toolchain/dx.jar` — compilado do código-fonte de `platform/dalvik/dx` (espelho aosp-mirror/platform_dalvik) | Apache License 2.0 |
| **aapt2** | baixado por `build/fetch_toolchain.py` de dl.google.com (build-tools 30.0.3) — não incluído | Apache License 2.0 (termos do Android SDK) |
| **android.jar** (API 28) | baixado por `build/fetch_toolchain.py` de dl.google.com — não incluído | Termos do Android SDK |
| **adb-shell**, **rsa**, **pyasn1**, **zeroconf** | instalados via `pip` (requirements.txt) | Apache-2.0 / Apache-2.0 / BSD-2 / LGPL-2.1 |

Papéis de parede (`app/assets/wallpapers/`) e ícones foram gerados para este projeto (MIT).

## Marcas
Apple, Liquid Glass, Google, Android TV, YouTube, TCL, Semp, Netflix, Globo e Globoplay são marcas
dos seus respectivos donos. Este é um projeto pessoal **sem afiliação** com nenhuma delas.
O módulo `atalho-globoplay/` existe só para remapear um botão físico do controle da sua própria TV;
não publique esse APK em lojas nem o distribua como se fosse o app da Globo.
