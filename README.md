# ControlBridge 🎮📱📺

MVP Android em dois aplicativos:
- `mobile`: recebe eventos de um controle Bluetooth conectado ao celular e envia comandos UDP pela rede local.
- `tv`: recebe e mostra os comandos na Android TV.

## Como testar
1. Abra o projeto no Android Studio.
2. Instale `tv` na Android TV.
3. Descubra o IP da TV.
4. Instale `mobile` no celular.
5. Pareie o controle Bluetooth com o celular.
6. Digite o IP da TV no app mobile e toque em **CONECTAR À TV**.
7. Pressione botões/mova os analógicos. A TV deverá mostrar os eventos.

## Limitação importante
Um app Android comum não possui permissão para injetar arbitrariamente eventos de gamepad em qualquer outro aplicativo/jogo na TV. Este projeto implementa a ponte de baixa latência e o receptor; transformar os pacotes recebidos em um gamepad virtual universal exige APIs/permissões específicas do dispositivo, ou suporte do jogo/serviço.

## Próxima evolução
- descoberta automática da TV via UDP/mDNS;
- QR Code para pareamento;
- autenticação do pareamento;
- compressão/batching de eventos;
- métricas de latência;
- modo compatível com jogos/serviços que aceitem entrada por rede.
