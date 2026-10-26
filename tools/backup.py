#!/usr/bin/env python3
"""
backup.py - FACA ISTO ANTES DE QUALQUER ALTERACAO.

Salva em backups/<data-hora>/: lista completa de pacotes, desativados, getprop, configuracoes
(system/secure/global), apps que respondem por HOME, entradas de TV, tamanho/densidade da tela.

  python tools/backup.py --host 192.168.0.50

ATENCAO: o backup contem dados do aparelho (numero de serie, MAC, ESN da Netflix...).
A pasta backups/ esta no .gitignore - nao publique.
"""
import argparse
import os
import time

from tvadb import ROOT, TV, add_host_arg

ITEMS = [
    ("packages-all.txt", "pm list packages -f -U"),
    ("packages-disabled.txt", "pm list packages -d"),
    ("packages-system.txt", "pm list packages -s"),
    ("packages-3rdparty.txt", "pm list packages -3"),
    ("getprop.txt", "getprop"),
    ("settings-system.txt", "settings list system"),
    ("settings-secure.txt", "settings list secure"),
    ("settings-global.txt", "settings list global"),
    ("home-activities.txt", "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("home-resolved.txt", "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME"),
    ("tv-inputs.txt", "dumpsys tv_input"),
    ("wm.txt", "wm size; wm density"),
    ("usb.txt", "dumpsys usb"),
    ("connectivity-transports.txt", "dumpsys connectivity | grep -o 'Transports: [A-Z|]*'"),
    ("wifi-owner.txt", "dumpsys wifi | grep -o 'cname=[^ ]*'"),
]


def main():
    ap = argparse.ArgumentParser()
    add_host_arg(ap)
    a = ap.parse_args()
    tv = TV(a.host, a.port)
    out = os.path.join(ROOT, "backups", time.strftime("%Y-%m-%d_%H%M%S"))
    os.makedirs(out)
    for name, cmd in ITEMS:
        with open(os.path.join(out, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(tv.sh(cmd))
        print("ok", name)
    print("captura:", tv.screenshot(os.path.join(out, "tela.png")), "bytes")
    sdk = tv.sh("getprop ro.build.version.sdk").strip()
    print(f"\nBackup em {out}\nAndroid SDK da TV: {sdk} (o Vidro precisa de 24+; testado no 28)")
    tv.close()


if __name__ == "__main__":
    main()
