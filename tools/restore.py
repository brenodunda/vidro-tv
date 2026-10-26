#!/usr/bin/env python3
"""
restore.py - DESFAZ tudo:
  * reativa todos os pacotes listados em state/disabled.txt (o launcher original primeiro);
  * volta a tela inicial original;
  * DNS Privado volta ao automatico;
  * remove o atalho do botao Globoplay (se instalado);
  * --remove-vidro tambem desinstala o Vidro.

  python tools/restore.py --host 192.168.0.50
  python tools/restore.py --host 192.168.0.50 --remove-vidro
  python tools/restore.py --home com.google.android.tvlauncher/.MainActivity   (padrao)

Sem PC: Configuracoes > Apps > Ver todos os apps > Apps do sistema > (app) > Ativar.
"""
import argparse
import os
import time

from tvadb import ROOT, TV, add_host_arg

FIRST = ("com.google.android.tvlauncher", "com.google.android.tvrecommendations")


def main():
    ap = argparse.ArgumentParser()
    add_host_arg(ap)
    ap.add_argument("--home", default="com.google.android.tvlauncher/.MainActivity",
                    help="activity da tela inicial original (veja backups/*/home-resolved.txt)")
    ap.add_argument("--remove-vidro", action="store_true")
    a = ap.parse_args()
    tv = TV(a.host, a.port)

    lst = os.path.join(ROOT, "state", "disabled.txt")
    pkgs = []
    if os.path.exists(lst):
        for l in open(lst, encoding="utf-8"):
            p = l.split("#")[0].strip()
            if p and p not in pkgs:
                pkgs.append(p)
    pkgs.sort(key=lambda p: 0 if p in FIRST else 1)   # a TV nunca fica sem tela inicial
    for p in pkgs:
        print(f"pm enable {p}: {tv.sh('pm enable ' + p).strip()}")
        time.sleep(0.3)

    print("tela inicial:", tv.sh(f"cmd package set-home-activity {a.home}").strip())
    tv.sh("settings put global private_dns_mode opportunistic")
    tv.sh("settings delete global private_dns_specifier")
    print("DNS Privado: automatico")
    if "com.globo.globotv" in tv.sh("pm list packages com.globo.globotv"):
        label = tv.sh("dumpsys package com.globo.globotv | grep versionName").strip()
        if "atalho" in label:                 # so remove o NOSSO atalho, nunca o Globoplay real
            print("atalho Globoplay:", tv.sh("pm uninstall com.globo.globotv").strip())
    if a.remove_vidro:
        print("Vidro:", tv.sh("pm uninstall com.breno.tvhome").strip())
    tv.key("HOME", delay=2)
    print("\nainda desativados:\n" + ("\n".join(tv.disabled()) or "(nenhum)"))
    tv.close()


if __name__ == "__main__":
    main()
