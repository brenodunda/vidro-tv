#!/usr/bin/env python3
"""
relay.py <ip da TV> [porta local] - repassa 127.0.0.1:15555 -> <TV>:5555.

Use quando um ambiente isolado nao alcanca a rede local mas um terminal comum alcanca:
deixe este script rodando e use TV_HOST=127.0.0.1 TV_PORT=15555 nas outras ferramentas.
"""
import socket, sys, threading

tv = sys.argv[1] if len(sys.argv) > 1 else sys.exit("uso: python tools/relay.py <ip da TV> [porta local]")
lport = int(sys.argv[2]) if len(sys.argv) > 2 else 15555


def pipe(a, b):
    try:
        while True:
            data = a.recv(65536)
            if not data:
                break
            b.sendall(data)
    except OSError:
        pass
    finally:
        for s in (a, b):
            try: s.close()
            except OSError: pass


srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", lport))
srv.listen(4)
print(f"relay 127.0.0.1:{lport} -> {tv}:5555 (Ctrl+C para sair)")
while True:
    c, _ = srv.accept()
    t = socket.create_connection((tv, 5555), timeout=10)
    t.settimeout(None)
    threading.Thread(target=pipe, args=(c, t), daemon=True).start()
    threading.Thread(target=pipe, args=(t, c), daemon=True).start()
