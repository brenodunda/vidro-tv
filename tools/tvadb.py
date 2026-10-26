"""
tvadb.py - conexao ADB com a TV em Python puro (pacote adb-shell), usada por todas as ferramentas.

Configuracao (variaveis de ambiente ou argumento --host nas ferramentas):
    TV_HOST   IP da TV (obrigatorio, ex.: 192.168.0.50)
    TV_PORT   porta ADB (padrao 5555; 15555 com o relay.py)
A chave ADB deste PC fica em tools/.adbkey (NUNCA envie para o Git; ja esta no .gitignore).
"""
import base64
import hashlib
import os
import sys
import time

try:
    from adb_shell.adb_device import AdbDeviceTcp
    from adb_shell.auth.keygen import keygen
    from adb_shell.auth.sign_pythonrsa import PythonRSASigner
except ImportError:
    sys.exit("Falta o pacote adb-shell. Rode:  python -m pip install -r requirements.txt")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KEY = os.path.join(HERE, ".adbkey")


def host_from(argv_host=None):
    h = argv_host or os.environ.get("TV_HOST")
    if not h:
        sys.exit("Informe o IP da TV: --host 192.168.x.y  (ou defina TV_HOST). "
                 "Nao sabe o IP? Rode: python tools/discover.py")
    return h


class TV:
    def __init__(self, host=None, port=None, auth_timeout=120):
        self.host = host_from(host)
        self.port = int(port or os.environ.get("TV_PORT", "5555"))
        if not os.path.exists(KEY):
            keygen(KEY)
        with open(KEY) as f:
            priv = f.read()
        with open(KEY + ".pub") as f:
            pub = f.read()
        self.signer = PythonRSASigner(pub, priv)
        self.dev = AdbDeviceTcp(self.host, self.port, default_transport_timeout_s=30)
        print(f"conectando a {self.host}:{self.port}... (se a TV perguntar 'Permitir depuracao USB?', aceite)")
        try:
            self.dev.connect(rsa_keys=[self.signer], auth_timeout_s=auth_timeout)
        except ConnectionRefusedError:
            sys.exit("Conexao recusada: ligue 'Depuracao USB' em Opcoes do desenvolvedor e, se preciso, "
                     "reinicie a TV (em varias TCL a porta 5555 so abre apos reiniciar).")

    # ------------------------------------------------------------ basico
    def sh(self, cmd, timeout=120):
        """Um comando por chamada (comandos encadeados podem parar no primeiro em alguns firmwares)."""
        return self.dev.shell(cmd, timeout_s=timeout, transport_timeout_s=timeout, read_timeout_s=timeout)

    def key(self, *keys, delay=0.4):
        for k in keys:
            k = str(k)
            k = k if k.isdigit() or k.startswith("KEYCODE_") else "KEYCODE_" + k
            self.sh(f"input keyevent {k}")
            time.sleep(delay)

    def screenshot(self, local):
        self.sh("screencap -p /data/local/tmp/_shot.png", timeout=30)
        os.makedirs(os.path.dirname(os.path.abspath(local)), exist_ok=True)
        self.dev.pull("/data/local/tmp/_shot.png", local, read_timeout_s=60)
        size = os.path.getsize(local)
        if size < 1000:   # TV ao vivo / HDMI / Netflix: captura vazia por protecao de conteudo
            print("captura vazia (conteudo protegido). Em primeiro plano:",
                  self.sh("dumpsys activity activities | grep -E 'mResumedActivity'").strip())
        return size

    def push(self, local, remote):
        want = hashlib.md5(open(local, "rb").read()).hexdigest()
        try:
            self.dev.push(local, remote, read_timeout_s=60)
        except Exception as e:
            print(f"push travou ({e!r}); usando blocos base64")
        if self.sh(f"md5sum '{remote}'").split()[:1] == [want]:
            return
        data = base64.b64encode(open(local, "rb").read()).decode()
        self.sh(f"rm -f '{remote}.b64'")
        for i in range(0, len(data), 30000):
            self.sh(f"echo -n '{data[i:i + 30000]}' >> '{remote}.b64'")
        self.sh(f"base64 -d '{remote}.b64' > '{remote}' && rm -f '{remote}.b64'")
        got = self.sh(f"md5sum '{remote}'").split()[0]
        if got != want:
            sys.exit(f"MD5 diferente apos envio: {got} != {want}")

    def pull(self, remote, local):
        os.makedirs(os.path.dirname(os.path.abspath(local)), exist_ok=True)
        self.dev.pull(remote, local, read_timeout_s=60)

    def install(self, apk):
        """Envia e instala com pm install -r (o 'adb install' pode travar esperando confirmacao na tela)."""
        self.push(apk, "/data/local/tmp/_install.apk")
        out = self.sh("pm install -r /data/local/tmp/_install.apk", timeout=240).strip()
        self.sh("rm -f /data/local/tmp/_install.apk")
        return out

    def disabled(self):
        return sorted(l.replace("package:", "").strip()
                      for l in self.sh("pm list packages -d").splitlines() if l.strip())

    def close(self):
        try:
            self.dev.close()
        except Exception:
            pass


def add_host_arg(ap):
    ap.add_argument("--host", help="IP da TV (ou variavel TV_HOST)")
    ap.add_argument("--port", help="porta ADB (padrao 5555)")
