#!/usr/bin/env python3
"""
install.py - instala o Vidro e, SE VOCE PEDIR, faz dele a tela inicial.

  python tools/install.py                         # so instala/atualiza dist/Vidro.apk (abre como app)
  python tools/install.py --set-home              # define o Vidro como tela inicial
  python tools/install.py --set-home --disable-stock   # e desativa o launcher original
  python tools/install.py --atalho-globoplay      # botao Globoplay (TCL) -> YouTube (ver docs)
  python tools/install.py --dns adguard|off       # DNS Privado com bloqueio de anuncios

Tudo e reversivel com tools/restore.py.
"""
import argparse
import os
import time

from tvadb import ROOT, TV, add_host_arg

VIDRO = "com.breno.tvhome/.HomeActivity"
STOCK = ["com.google.android.tvlauncher", "com.google.android.tvrecommendations"]


def home_now(tv):
    out = tv.sh("cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME")
    for l in out.splitlines():
        if "packageName=" in l:
            return l.split("=", 1)[1].strip()
    return "?"


def record(names, title):
    os.makedirs(os.path.join(ROOT, "state"), exist_ok=True)
    with open(os.path.join(ROOT, "state", "disabled.txt"), "a", encoding="utf-8") as f:
        f.write(f"# {title} ({time.strftime('%Y-%m-%d %H:%M')})\n" + "".join(n + "\n" for n in names))


def main():
    ap = argparse.ArgumentParser()
    add_host_arg(ap)
    ap.add_argument("--apk", default=os.path.join(ROOT, "dist", "Vidro.apk"))
    ap.add_argument("--set-home", action="store_true")
    ap.add_argument("--disable-stock", action="store_true")
    ap.add_argument("--atalho-globoplay", action="store_true")
    ap.add_argument("--dns", choices=["adguard", "off"])
    a = ap.parse_args()
    tv = TV(a.host, a.port)

    sdk = int(tv.sh("getprop ro.build.version.sdk").strip() or 0)
    if sdk < 24:
        raise SystemExit(f"Android SDK {sdk}: o Vidro precisa de Android 7 (SDK 24) ou mais novo.")
    if os.path.exists(a.apk):
        print("instalando", a.apk, "->", tv.install(a.apk))

    if a.set_home:
        print("candidatos a HOME:\n" + tv.sh(
            "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME"
            " | grep packageName= | sort -u"))
        print("set-home-activity:", tv.sh(f"cmd package set-home-activity {VIDRO}").strip())
    if a.disable_stock:
        tv.key("HOME", delay=3)
        if "com.breno.tvhome" not in tv.sh("dumpsys activity activities | grep mResumedActivity"):
            raise SystemExit("O HOME nao abriu o Vidro; nao vou desativar o launcher original.")
        done = []
        for p in STOCK:
            out = tv.sh(f"pm disable-user --user 0 {p}").strip()
            print(out)
            if "disabled" in out:
                done.append(p)
        record(done, "launcher original")
        tv.sh(f"cmd package set-home-activity {VIDRO}")
    if a.atalho_globoplay:
        apk = os.path.join(ROOT, "dist", "Atalho-Globoplay.apk")
        if not os.path.exists(apk):
            raise SystemExit("Compile antes: python build/build.py atalho-globoplay")
        print("atalho Globoplay -> YouTube:", tv.install(apk))
    if a.dns == "adguard":
        tv.sh("settings put global private_dns_specifier dns.adguard-dns.com")
        tv.sh("settings put global private_dns_mode hostname")
        print("DNS Privado: dns.adguard-dns.com")
    elif a.dns == "off":
        tv.sh("settings put global private_dns_mode opportunistic")
        print("DNS Privado: automatico")

    tv.key("HOME", delay=2.5)
    print("tela inicial atual:", home_now(tv))
    print("captura:", tv.screenshot(os.path.join(ROOT, "shots", "install.png")), "bytes")
    tv.close()


if __name__ == "__main__":
    main()
