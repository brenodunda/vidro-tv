#!/usr/bin/env python3
"""
debloat.py - desativa pacotes em lotes, com confirmacao, captura de tela e verificacao.

  python tools/debloat.py --host 192.168.0.50 config/debloat-tcl-android9.txt
  python tools/debloat.py config/debloat-tcl-android9.txt --dry-run      (so mostra)

Regras de seguranca embutidas:
  * so "pm disable-user --user 0" (nunca desinstala); um comando pm por chamada;
  * recusa pacotes essenciais do Android e da TV (lista PROTECTED abaixo);
  * recusa qualquer pacote de fabrica (factory / fac / AT);
  * para se TODOS os comandos de um lote forem recusados pela TV;
  * confere com "pm list packages -d" (nao confia so na saida do comando);
  * grava cada pacote desativado em state/disabled.txt (o restore.py reativa tudo).
FACA O BACKUP ANTES:  python tools/backup.py
"""
import argparse
import os
import re
import sys
import time

from tvadb import ROOT, TV, add_host_arg

PROTECTED_EXACT = {
    "android", "com.android.systemui", "com.android.shell", "com.android.phone", "com.android.settings",
    "com.android.tv.settings", "com.android.vending", "com.google.android.gms", "com.google.android.gsf",
    "com.android.packageinstaller", "com.google.android.packageinstaller",
    "com.android.permissioncontroller", "com.google.android.permissioncontroller",
    "com.android.externalstorage", "com.android.sharedstoragebackup", "com.android.providers.settings",
    "com.android.providers.media", "com.android.providers.downloads", "com.android.providers.tv",
    "com.google.android.ext.services", "com.google.android.ext.shared", "com.android.bluetooth",
    "com.google.android.inputmethod.latin", "com.android.inputdevices", "com.android.defcontainer",
    "com.google.android.tungsten.setupwraith", "com.google.android.tv.remote.service",
    "com.tcl.tv", "com.tcl.tvinput", "com.tcl.tcl_bt_rcu_service", "com.tcl.autopair",
    "com.tcl.keycustomfunctionservice", "com.tcl.settings", "com.tcl.framework.custom",
    "com.tcl.inputmethod.international", "com.tcl.providers.config", "com.tcl.systemuioverlay",
}
PROTECTED_PREFIX = ("com.google.android.overlay.modules", "com.android.providers.", "com.android.server.")
PROVIDERS_OK = {"com.android.providers.calendar", "com.android.providers.contacts",
                "com.android.providers.userdictionary", "com.android.providers.blockednumber"}
FACTORY = re.compile(r"(factory|\.fac(\.|$)|fac\.menu|\.at\.|factorymenu)", re.I)


def protected(p):
    if p in PROTECTED_EXACT or FACTORY.search(p):
        return True
    return p.startswith(PROTECTED_PREFIX) and p not in PROVIDERS_OK


def parse(path):
    batches, cur = [], None
    for line in open(path, encoding="utf-8"):
        s = line.strip()
        if s.startswith("##"):
            cur = (s.lstrip("# ").strip(), [])
            batches.append(cur)
        elif s and not s.startswith("#"):
            pkg = s.split("#")[0].strip()
            if cur is None:
                cur = ("Lote", [])
                batches.append(cur)
            cur[1].append(pkg)
    return [b for b in batches if b[1]]


def main():
    ap = argparse.ArgumentParser()
    add_host_arg(ap)
    ap.add_argument("lista", help="arquivo de lotes (ex.: config/debloat-tcl-android9.txt)")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--yes", action="store_true", help="nao perguntar entre lotes")
    a = ap.parse_args()
    batches = parse(a.lista)
    for name, pkgs in batches:
        bad = [p for p in pkgs if protected(p)]
        if bad:
            sys.exit(f"Lote '{name}' contem pacote protegido: {bad}. Remova-o da lista.")
    if a.dry_run:
        for name, pkgs in batches:
            print(f"\n{name}:\n  " + "\n  ".join(pkgs))
        return

    tv = TV(a.host, a.port)
    installed = set(l.replace("package:", "").strip() for l in tv.sh("pm list packages").splitlines())
    os.makedirs(os.path.join(ROOT, "state"), exist_ok=True)
    log = os.path.join(ROOT, "state", "disabled.txt")
    shots = os.path.join(ROOT, "shots")
    for n, (name, pkgs) in enumerate(batches, 1):
        pkgs = [p for p in pkgs if p in installed]
        if not pkgs:
            continue
        print(f"\n=== {name} ===\n  " + "\n  ".join(pkgs))
        if not a.yes and input("Desativar este lote? [s/N] ").strip().lower() not in ("s", "sim", "y"):
            continue
        refused = 0
        for p in pkgs:
            out = tv.sh(f"pm disable-user --user 0 {p}").strip()   # um pm por chamada
            print(" ", out or p)
            if "disabled" not in out:
                refused += 1
            time.sleep(0.3)
        now = set(tv.disabled())
        done = [p for p in pkgs if p in now]
        with open(log, "a", encoding="utf-8") as f:
            f.write(f"# {name} ({time.strftime('%Y-%m-%d %H:%M')})\n" + "".join(p + "\n" for p in done))
        if refused == len(pkgs) and not done:
            sys.exit("A TV recusou TODOS os comandos deste lote. Parei aqui - verifique antes de continuar.")
        tv.key("HOME", delay=2.5)
        print(f"  conferido em 'pm list packages -d': {len(done)}/{len(pkgs)}  |  captura:",
              tv.screenshot(os.path.join(shots, f"debloat-{n:02d}.png")), "bytes")
    print(f"\nFeito. Lista do que foi desativado: {log}\n"
          "Reinicie a TV e confira com: python tools/tv.py --shell \"pm list packages -d\" "
          "(alguns firmwares reativam pacotes no boot).")
    tv.close()


if __name__ == "__main__":
    main()
