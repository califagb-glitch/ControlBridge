# ControlBridge 🎮📱📺

O ControlBridge agora segue uma arquitetura inspirada no conceito de **Phone Controller** do Amazon Luna: o celular é o controlador e transporta os eventos pela rede local para um receptor compatível. O telefone **não tenta se passar por um gamepad Bluetooth da TV**.

## Arquitetura atual

**manete Bluetooth → Android → ControlBridge → Wi‑Fi/LAN → receptor no navegador da TV**

A primeira versão usava `BluetoothHidDevice` + `AccessibilityService`. Isso foi removido porque exigia permissões especiais, podia atrapalhar o pareamento da manete e não era a melhor solução para o objetivo do projeto.

O Android já expõe eventos de gamepad (`KeyEvent` e `MotionEvent`) para aplicativos compatíveis com controles. O ControlBridge captura esses eventos enquanto sua tela está em primeiro plano e os envia pelo LAN. citehttps://developer.android.com/games/sdk/game-controller/compatibility?hl=pt-BR

## Como usar

1. Pareie a manete com o celular normalmente nas configurações Bluetooth do Android.
2. Abra o ControlBridge. **Nenhuma permissão Bluetooth especial é solicitada pelo app.**
3. Mantenha o ControlBridge aberto para que ele receba os eventos do controle.
4. O app mostra o endereço local, por exemplo `http://192.168.1.20:8080`.
5. Na TV Samsung, abra o navegador e digite esse endereço.
6. A página **ControlBridge TV Receiver** abre e conecta automaticamente ao WebSocket do celular.
7. Pressione botões ou mova os analógicos: o receptor mostra os eventos recebidos.

## Importante sobre a TV Samsung/Tizen

O receptor web é uma camada de comunicação/teste. Ele **não consegue injetar comandos em qualquer aplicativo nativo da Samsung TV**. Uma página web não pode transformar arbitrariamente eventos WebSocket em entrada nativa de um jogo externo.

Portanto, para controlar um jogo específico na TV, esse jogo precisa oferecer uma integração compatível com o protocolo do ControlBridge, ou o jogo precisa estar rodando no próprio navegador/receptor. Não prometemos uma injeção universal de controles no Tizen.

## Filosofia do projeto

Queremos reproduzir as partes úteis do conceito Luna Phone Controller:

- celular como controle;
- conexão por rede local;
- baixa latência;
- descoberta simples;
- interface de controle moderna;
- sem APK Android instalado na Samsung TV;
- sem AccessibilityService;
- sem Bluetooth HID Device;
- sem permissões de `BLUETOOTH_ADVERTISE`/`BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` no aplicativo.

O próprio Luna demonstra o conceito de smartphone como controle para experiências voltadas à TV, inclusive com entrada de jogadores por QR code em experiências GameNight. citehttps://aws.amazon.com/pt/solutions/amazon/one-amazon-lane/streaming/

## Interface

O HUD foi redesenhado para parecer um produto de gaming: status da manete, endereço do receptor, área de controle virtual e animações simples, mantendo o foco em baixa latência.

## Imagem solicitada

A imagem enviada anteriormente por URL não pôde ser recuperada do servidor neste ambiente. Ela não foi falsamente incorporada ao projeto. Se a imagem for anexada diretamente à conversa, ela poderá ser usada como asset do HUD.

## Limitações reais

- O Android normalmente entrega eventos do controle ao app que está em primeiro plano; não prometemos captura global sem privilégios.
- O receptor web recebe e visualiza os eventos, mas não pode injetá-los universalmente em jogos nativos da TV.
- O telefone e a TV precisam estar na mesma rede local para a conexão direta.
- O desempenho depende da rede; 5 GHz é recomendado quando disponível.
