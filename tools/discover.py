#!/usr/bin/env python3
"""
discover.py - encontra Android TVs na rede local (somente leitura, nao altera nada).

  python tools/discover.py                 # tabela ARP + porta 5555 + mDNS (Cast / controle remoto)
  python tools/discover.py --scan 192.168.0.50   # testa TODAS as portas TCP de um IP (1-65535)

Dica: o MAC da TV aparece em Configuracoes > Preferencias do dispositivo > Sobre > Status
(ou "Informacao do produto"); compare com a coluna MAC da tabela ARP.
"""
import argparse
import concurrent.futures as cf
import re
import socket
import subprocess
import time


def arp_hosts():
    out = subprocess.run(["arp", "-a"], capture_output=True, text=True, errors="replace").stdout
    rows = re.findall(r"(\d+\.\d+\.\d+\.\d+)\D+?([0-9a-fA-F]{2}(?:[-:][0-9a-fA-F]{2}){5})", out)
    return [(ip, mac.replace("-", ":").lower()) for ip, mac in rows
            if not ip.startswith(("224.", "239.", "255.")) and not ip.endswith(".255")]


def is_open(ip, port, timeout=0.6):
    try:
        with socket.create_connection((ip, port), timeout=timeout):
            return True
    except OSError:
        return False


def mdns(seconds=6):
    try:
        from zeroconf import ServiceBrowser, Zeroconf
    except ImportError:
        print("(mDNS ignorado: pip install zeroconf)")
        return []
    found = []

    class L:
        def add_service(self, zc, t, n):
            i = zc.get_service_info(t, n)
            found.append((t, n, i.parsed_addresses() if i else [], i.port if i else None))

        def update_service(self, *a): pass
        def remove_service(self, *a): pass

    zc = Zeroconf()
    types = ["_adb-tls-connect._tcp.local.", "_adb._tcp.local.", "_googlecast._tcp.local.",
             "_androidtvremote2._tcp.local."]
    _ = [ServiceBrowser(zc, t, L()) for t in types]
    time.sleep(seconds)
    zc.close()
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scan", metavar="IP", help="varre todas as portas TCP deste IP")
    a = ap.parse_args()
    if a.scan:
        print(f"varrendo 1-65535 em {a.scan} (alguns minutos)...")
        with cf.ThreadPoolExecutor(200) as ex:
            ports = [p for p, ok in zip(range(1, 65536),
                                        ex.map(lambda p: is_open(a.scan, p, 0.5), range(1, 65536))) if ok]
        print("portas abertas:", ports)
        print("ADB costuma ser 5555; 6466/6467 = controle remoto Android TV; 8008/8009 = Cast.")
        return
    print("IP               MAC                 5555  nome")
    with cf.ThreadPoolExecutor(64) as ex:
        hosts = arp_hosts()
        opens = list(ex.map(lambda h: is_open(h[0], 5555), hosts))
    for (ip, mac), op in zip(hosts, opens):
        try:
            name = socket.gethostbyaddr(ip)[0]
        except OSError:
            name = ""
        print(f"{ip:16} {mac:19} {'ABERTA' if op else '  -  '}  {name}")
    print("\nServicos mDNS:")
    for t, n, addrs, port in mdns():
        print(f"  {n}  {addrs}  porta {port}")


if __name__ == "__main__":
    main()
