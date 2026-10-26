# Solução de problemas

Os problemas abaixo apareceram de fato durante o projeto, com a causa e a correção de cada um.

| Sintoma | Causa | Solução |
|---|---|---|
| `Conexao recusada` na porta 5555 com a Depuração USB ligada | Em várias TCL, o ADB de rede só sobe **depois de reiniciar** | Reinicie a TV com a Depuração USB ligada; às vezes são precisas duas reinicializações |
| A porta 5555 nunca abre | O firmware pode usar outra porta, ou só a *depuração sem fio* (Android 11+) | `python tools/discover.py --scan <ip>` lista as portas abertas. Em Android 11+, use *Depuração sem fio › Parear com código* e `adb pair` |
| A TV não pergunta "Permitir depuração USB?" | `ro.adb.secure=0` no firmware (aceita qualquer um) ou autorização já concedida | Normal. **Feche o ADB ao terminar** |
| `python tools/...` parado por mais de 1 minuto na primeira execução (Windows) | O antivírus (por exemplo o Bitdefender) inspeciona o Python e a criação do venv | Espere; nas próximas vezes é rápido |
| O envio de arquivo (*push*) trava | Limitação do protocolo em alguns firmwares | O `tvadb.py` já cai sozinho para blocos base64 com verificação de MD5 |
| `adb install` fica parado | Espera uma confirmação na tela da TV | Use `tools/install.py` ou `tv.py --install` (que usam `pm install -r`) |
| A categoria ou a linha da grade não aparece | Uma view `WRAP_CONTENT` posicionada fora da área inicial da tela é medida com altura 0 | Use altura fixa (já corrigido no Vidro) |
| A captura de tela sai vazia (0 bytes ou preta) | TV ao vivo, HDMI e Netflix são protegidos | O `tvadb.screenshot` mostra qual app está em primeiro plano |
| A captura mostra um app que já foi fechado | O foco ainda está nele | `dumpsys window \| grep mFocusedApp`, depois `am force-stop <pacote>` e capture de novo |
| A tela parece ampliada ou cortada | `wm size`/`wm density` alterados | `tv.py --shell "wm size reset"` e `"wm density reset"` |
| O botão de app do controle não é remapeado pela acessibilidade | O controle TCL **injeta** o keycode sem passar pelo filtro de acessibilidade | Use o atalho de pacote ([BOTAO-GLOBOPLAY-YOUTUBE](BOTAO-GLOBOPLAY-YOUTUBE.md)) |
| Ao apertar HOME aparece "Concluir ação usando…" | Há duas telas iniciais e nenhuma padrão | `python tools/install.py --set-home` |
| A TV ficou sem tela inicial | O launcher foi desativado antes de o Vidro virar padrão | `tv.py --shell "pm enable com.google.android.tvlauncher"`, ou rode `restore.py` |
| O APK novo não instala ("signatures do not match") | Foi compilado com outra chave | Use a mesma `build/keystore/*.jks` (variáveis `VIDRO_KEYSTORE*`) ou desinstale antes (perde os ajustes) |
| Um pacote desativado voltou depois de reiniciar | O firmware reativa no boot | Anote no cabeçalho da lista e não brigue com o firmware |
| Pacote com nome *factory*, *fac menu* ou *AT* | **Nunca abra** (pode ativar o modo fábrica) | O `debloat.py` recusa esses pacotes. Para inspecionar, use só `dumpsys package <nome>` |
