#!/usr/bin/env python3
"""
adb_closed.py <ip da TV> - confere se o ADB de rede da TV esta FECHADO.

Testa a porta 5555 (e 5037/5554/5556). "FECHADA" = conexao recusada ou sem resposta.
Uso:  python tools/adb_closed.py 192.168.0.50
Saida com codigo 0 se todas fechadas, 1 se alguma aberta.
"""
import os, socket, sys

host = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("TV_HOST") or sys.exit("uso: python tools/adb_closed.py <ip da TV>")
opened = []
for port in (5555, 5037, 5554, 5556):
    try:
        with socket.create_connection((host, port), timeout=2):
            opened.append(port)
            print(f"porta {port}: ABERTA  <-- ADB de rede ainda ativo")
    except ConnectionRefusedError:
        print(f"porta {port}: fechada (recusou a conexao)")
    except OSError:
        print(f"porta {port}: fechada (sem resposta)")
if opened:
    print("\nDesative 'Depuracao USB' em Opcoes do desenvolvedor e reinicie a TV.")
    sys.exit(1)
print("\nOK: o ADB de rede esta fechado.")
