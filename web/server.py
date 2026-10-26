#!/usr/bin/env python3
"""
Pagina do projeto para o Replit (ou qualquer PC): compila o APK em segundo plano e serve
uma pagina com capturas, recursos e o link de download.

    python web/server.py            -> http://localhost:8080  (porta: variavel PORT)

No Replit basta clicar em "Run" (.replit ja aponta para este arquivo).
Observacao: o Replit roda na nuvem e NAO alcanca a TV da sua casa; para instalar na TV use as
ferramentas de tools/ no seu PC (docs/PASSO-A-PASSO.md).
"""
import http.server
import json
import os
import subprocess
import sys
import threading
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STATE = {"status": "aguardando", "log": "", "started": time.time()}


def build():
    STATE["status"] = "compilando"
    steps = [[sys.executable, os.path.join(ROOT, "build", "fetch_toolchain.py")],
             [sys.executable, os.path.join(ROOT, "build", "build.py")]]
    for cmd in steps:
        if cmd[-1].endswith("fetch_toolchain.py") and os.path.exists(os.path.join(ROOT, "build", "toolchain", "android.jar")):
            continue
        p = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
        STATE["log"] += p.stdout[-4000:] + p.stderr[-4000:]
        if p.returncode != 0:
            STATE["status"] = "erro"
            return
    STATE["status"] = "pronto" if os.path.exists(os.path.join(ROOT, "dist", "Vidro.apk")) else "erro"


class H(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self.path = "/web/index.html"
        elif self.path == "/status":
            body = json.dumps({"status": STATE["status"], "log": STATE["log"][-3000:]}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        elif not self.path.startswith(("/web/", "/docs/", "/dist/")):
            self.send_error(404)
            return
        super().do_GET()

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    threading.Thread(target=build, daemon=True).start()
    port = int(os.environ.get("PORT", "8080"))
    print(f"Vidro: http://0.0.0.0:{port}  (compilando o APK em segundo plano...)")
    http.server.ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
