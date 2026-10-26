#!/usr/bin/env python3
"""
Compila os APKs do projeto SEM Gradle e SEM Android Studio.

    python build/build.py                    -> dist/Vidro.apk            (launcher)
    python build/build.py atalho-globoplay   -> dist/Atalho-Globoplay.apk (opcional)

Requisitos: Python 3.8+ e JDK 11+ (java, javac, keytool, jarsigner no PATH).

Ferramentas Android (resolvidas nesta ordem):
  aapt2       : $AAPT2 -> build/toolchain/aapt2[.exe] -> aapt2 no PATH -> $ANDROID_HOME/build-tools/*/aapt2
  android.jar : $ANDROID_JAR -> build/toolchain/android.jar -> $ANDROID_HOME/platforms/android-28/android.jar
  dexador     : build/toolchain/dx.jar (AOSP dx, Apache-2.0, incluido no repositorio)
Se faltar algo, rode antes:  python build/fetch_toolchain.py   (baixa do repositorio oficial do Google)

Assinatura: build/keystore/vidro.jks e criado na primeira compilacao (NAO vai para o Git).
Guarde esse arquivo: atualizacoes do app precisam da mesma chave.
Variaveis: VIDRO_KEYSTORE (caminho), VIDRO_KEYSTORE_PASS (padrao "vidro-local"),
VIDRO_KEY_ALIAS (padrao "vidro") - para continuar usando uma chave que voce ja tem.
"""
import glob
import os
import shutil
import struct
import subprocess
import sys
import zipfile

BUILD = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(BUILD)
TC = os.path.join(BUILD, "toolchain")
EXE = ".exe" if os.name == "nt" else ""
PROJECTS = {"app": "Vidro.apk", "atalho-globoplay": "Atalho-Globoplay.apk"}


def sdk_dirs():
    for v in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(v):
            yield os.environ[v]
    home = os.path.expanduser("~")
    yield os.path.join(home, "AppData", "Local", "Android", "Sdk")   # Android Studio (Windows)
    yield os.path.join(home, "Android", "Sdk")                       # Android Studio (Linux)
    yield os.path.join(home, "Library", "Android", "sdk")            # Android Studio (macOS)


def find_aapt2():
    cands = [os.environ.get("AAPT2"), os.path.join(TC, "aapt2" + EXE), shutil.which("aapt2")]
    for s in sdk_dirs():
        cands += sorted(glob.glob(os.path.join(s, "build-tools", "*", "aapt2" + EXE)), reverse=True)
    for c in cands:
        if c and os.path.isfile(c):
            return c
    sys.exit("aapt2 nao encontrado. Rode: python build/fetch_toolchain.py")


def find_android_jar():
    cands = [os.environ.get("ANDROID_JAR"), os.path.join(TC, "android.jar")]
    for s in sdk_dirs():
        cands.append(os.path.join(s, "platforms", "android-28", "android.jar"))
    for c in cands:
        if c and os.path.isfile(c):
            return c
    sys.exit("android.jar (API 28) nao encontrado. Rode: python build/fetch_toolchain.py")


def run(*cmd):
    print(">", " ".join(str(c) for c in cmd), flush=True)
    subprocess.check_call([str(c) for c in cmd])


def zipalign(src, dst, align=4):
    """Alinha entradas sem compressao em 4 bytes (mesmo efeito do zipalign)."""
    zin = zipfile.ZipFile(src)
    with open(dst, "wb") as f:
        zout = zipfile.ZipFile(f, "w")
        for info in zin.infolist():
            data = zin.read(info.filename)
            ni = zipfile.ZipInfo(info.filename, date_time=(1981, 1, 1, 0, 0, 0))
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            if info.compress_type == zipfile.ZIP_STORED:
                off = f.tell() + 30 + len(info.filename.encode())
                ni.extra = b"\x00" * ((-off) % align)
            zout.writestr(ni, data)
        zout.close()


def build(project):
    src = os.path.join(ROOT, project)
    if not os.path.isfile(os.path.join(src, "AndroidManifest.xml")):
        sys.exit(f"projeto desconhecido: {project}")
    out = os.path.join(ROOT, "out", project)
    dist = os.path.join(ROOT, "dist")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(os.path.join(out, "classes"))
    os.makedirs(dist, exist_ok=True)
    aapt2, android_jar, dx = find_aapt2(), find_android_jar(), os.path.join(TC, "dx.jar")
    print(f"aapt2: {aapt2}\nandroid.jar: {android_jar}\n")

    # 1) recursos + manifesto
    res = []
    if os.path.isdir(os.path.join(src, "res")):
        run(aapt2, "compile", "--dir", os.path.join(src, "res"), "-o", os.path.join(out, "res.zip"))
        res = [os.path.join(out, "res.zip")]
    assets = ["-A", os.path.join(src, "assets")] if os.path.isdir(os.path.join(src, "assets")) else []
    run(aapt2, "link", "-o", os.path.join(out, "base.apk"), "-I", android_jar,
        "--manifest", os.path.join(src, "AndroidManifest.xml"), *assets,
        "--java", os.path.join(out, "gen"), "--min-sdk-version", "24", "--target-sdk-version", "28", *res)

    # 2) Java -> .class (Java 8, sem lambdas, compativel com dx)
    srcs = glob.glob(os.path.join(src, "src", "**", "*.java"), recursive=True) + \
        glob.glob(os.path.join(out, "gen", "**", "*.java"), recursive=True)
    run("javac", "-nowarn", "-encoding", "UTF-8", "-source", "8", "-target", "8",
        "-bootclasspath", android_jar, "-d", os.path.join(out, "classes"), *srcs)

    # 3) .class -> classes.dex
    run("java", "-jar", dx, "--dex", "--min-sdk-version=24",
        "--output=" + os.path.join(out, "classes.dex"), os.path.join(out, "classes"))

    # 4) dex dentro do APK
    unsigned = os.path.join(out, "unsigned.apk")
    shutil.copy(os.path.join(out, "base.apk"), unsigned)
    with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(out, "classes.dex"), "classes.dex")

    # 5) assinatura v1 (aceita no Android 7-10 com targetSdk 28) + alinhamento
    ks = os.environ.get("VIDRO_KEYSTORE", os.path.join(BUILD, "keystore", "vidro.jks"))
    pw = os.environ.get("VIDRO_KEYSTORE_PASS", "vidro-local")
    alias = os.environ.get("VIDRO_KEY_ALIAS", "vidro")
    if not os.path.exists(ks):
        os.makedirs(os.path.dirname(ks), exist_ok=True)
        run("keytool", "-genkeypair", "-keystore", ks, "-storepass", pw, "-keypass", pw,
            "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=Vidro TV Launcher, O=Pessoal, C=BR")
    run("jarsigner", "-keystore", ks, "-storepass", pw, "-sigalg", "SHA256withRSA",
        "-digestalg", "SHA-256", unsigned, alias)
    final = os.path.join(dist, PROJECTS.get(project, project + ".apk"))
    zipalign(unsigned, final)
    print("\nOK ->", final)
    return final


if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "app")
