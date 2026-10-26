#!/usr/bin/env python3
"""
tv.py - teclas, capturas de tela, shell e arquivos na TV.

  python tools/tv.py --host 192.168.0.50 --screenshot shots/01.png DPAD_RIGHT
  python tools/tv.py HOME DPAD_DOWN DPAD_CENTER          (com TV_HOST definido)
  python tools/tv.py --shell "pm list packages -d"
  python tools/tv.py --push foto.jpg /sdcard/Android/data/com.breno.tvhome/files/wallpaper.jpg HOME
  python tools/tv.py --install dist/Vidro.apk

Teclas: nomes KEYCODE sem o prefixo (HOME, BACK, DPAD_UP, DPAD_CENTER, MENU...) ou numeros.
"""
import argparse
import time

from tvadb import TV, add_host_arg


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    add_host_arg(ap)
    ap.add_argument("--screenshot", metavar="ARQ.png", help="salva a tela da TV depois das teclas")
    ap.add_argument("--shell", metavar="CMD", help="executa um comando no shell da TV")
    ap.add_argument("--push", nargs=2, metavar=("LOCAL", "REMOTO"), help="envia um arquivo")
    ap.add_argument("--pull", nargs=2, metavar=("REMOTO", "LOCAL"), help="copia um arquivo da TV")
    ap.add_argument("--install", metavar="APK", help="instala/atualiza um APK")
    ap.add_argument("keys", nargs="*", help="teclas a enviar")
    a = ap.parse_args()
    tv = TV(a.host, a.port)
    if a.install:
        print(tv.install(a.install))
    if a.push:
        tv.push(*a.push)
        print("enviado:", a.push[1])
    if a.pull:
        tv.pull(*a.pull)
        print("salvo:", a.pull[1])
    if a.keys:
        tv.key(*a.keys)
    if a.shell:
        print(tv.sh(a.shell))
    if a.screenshot:
        time.sleep(0.8)
        print("captura:", a.screenshot, tv.screenshot(a.screenshot), "bytes")
    tv.close()


if __name__ == "__main__":
    main()
