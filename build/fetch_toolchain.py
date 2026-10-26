#!/usr/bin/env python3
"""
Baixa do repositorio OFICIAL do Google (dl.google.com) as duas pecas do SDK que o build usa:
  * aapt2       (build-tools 30.0.3, Windows / Linux / macOS)
  * android.jar (plataforma Android 9 / API 28)
e coloca em build/toolchain/. Nada disso vai para o Git (licenca do Android SDK).

    python build/fetch_toolchain.py

Ao baixar, voce concorda com os termos do Android SDK:
https://developer.android.com/studio/terms
Ja tem o Android Studio? Nao precisa: o build encontra o SDK sozinho (ANDROID_HOME).
"""
import io
import os
import platform
import stat
import sys
import urllib.request
import zipfile

TC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "toolchain")
REPO = "https://dl.google.com/android/repository/"
PLATFORM_ZIP = "platform-28_r06.zip"
BUILD_TOOLS = {"Windows": "build-tools_r30.0.3-windows.zip",
               "Linux": "build-tools_r30.0.3-linux.zip",
               "Darwin": "build-tools_r30.0.3-macosx.zip"}


def download(name):
    url = REPO + name
    print("baixando", url)
    with urllib.request.urlopen(url, timeout=120) as r:
        total = int(r.headers.get("Content-Length", 0))
        buf, got = io.BytesIO(), 0
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            buf.write(chunk)
            got += len(chunk)
            if total:
                print(f"\r  {got * 100 // total:3d}%  ({got // (1 << 20)} MB)", end="", flush=True)
        print()
    return zipfile.ZipFile(buf)


def extract(z, basename, dest, executable=False):
    for n in z.namelist():
        if n.endswith("/" + basename):
            with open(dest, "wb") as f:
                f.write(z.read(n))
            if executable:
                os.chmod(dest, os.stat(dest).st_mode | stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
            print("  ->", dest)
            return True
    return False


def main():
    os.makedirs(TC, exist_ok=True)
    jar = os.path.join(TC, "android.jar")
    if not os.path.exists(jar):
        if not extract(download(PLATFORM_ZIP), "android.jar", jar):
            sys.exit("android.jar nao encontrado no pacote da plataforma")
    else:
        print("ja existe:", jar)

    exe = "aapt2.exe" if os.name == "nt" else "aapt2"
    a2 = os.path.join(TC, exe)
    if not os.path.exists(a2):
        z = download(BUILD_TOOLS.get(platform.system(), BUILD_TOOLS["Linux"]))
        if not extract(z, exe, a2, executable=True):
            sys.exit("aapt2 nao encontrado no pacote build-tools")
        if os.name == "nt":                      # o aapt2.exe do Windows precisa da DLL ao lado
            for n in z.namelist():
                if n.lower().endswith(".dll"):
                    extract(z, n.split("/")[-1], os.path.join(TC, n.split("/")[-1]))
    else:
        print("ja existe:", a2)
    print("\nPronto. Agora: python build/build.py")


if __name__ == "__main__":
    main()
