# ControlBridge 🎮📱📺

O ControlBridge transforma o **celular em um gamepad Bluetooth real**.

Fluxo:

**manete Bluetooth → celular → Bluetooth HID → TV → jogo**

A TV não precisa instalar o APK do ControlBridge. O celular anuncia um perfil HID de gamepad e a TV o enxerga como uma manete Bluetooth comum. O app usa `BluetoothHidDevice` (Android 9/API 28+) e envia relatórios HID com dois analógicos, gatilhos, D-pad e até 16 botões.

## Como usar

1. Instale o APK `mobile` no celular.
2. Ligue o Bluetooth e dê a permissão de **Dispositivos próximos**.
3. Abra o ControlBridge e deixe a tela aberta durante o primeiro pareamento.
4. Ative o serviço de acessibilidade do ControlBridge em **Configurações → Acessibilidade**. Ele captura os eventos da manete globalmente.
5. Na TV, abra as configurações de Bluetooth e procure **ControlBridge Gamepad**.
6. Pareie a TV com o ControlBridge.
7. Volte ao app, toque em **Atualizar dispositivos pareados**, selecione a TV e toque em **Conectar como gamepad**.
8. Deixe a notificação do ControlBridge ativa. Agora os botões e analógicos da manete conectada ao celular são enviados para a TV como entrada de controle.

## Importante para o pareamento

O registro HID precisa estar ativo **antes** do pareamento, porque a TV pode armazenar em cache o perfil HID. Se a TV já tinha pareado o celular como um telefone Bluetooth, remova esse pareamento dos dois lados e faça novamente procurando o nome **ControlBridge Gamepad**.

## Compatibilidade

O recurso principal depende de o firmware do celular oferecer o perfil **Bluetooth HID Device**. A API pública existe desde o Android 9, mas alguns fabricantes podem não expor o perfil no aparelho. O app detecta essa situação e informa que o HID não está disponível.

A captura global da manete usa `AccessibilityService` para receber eventos de gamepad mesmo fora da tela do ControlBridge. Os eventos são convertidos para o relatório HID e enviados diretamente para a TV, sem UDP e sem depender de um aplicativo receptor na TV.

## TV Samsung

Este desenho é especialmente importante para TVs Samsung/Tizen: não tentamos instalar um APK Android na TV. A TV recebe o ControlBridge através do Bluetooth como um gamepad. As TVs Samsung possuem suporte a gamepads Bluetooth/USB, embora a compatibilidade do jogo específico dependa do próprio jogo.

## Estrutura

- `mobile`: aplicativo principal, Bluetooth HID, captura global e interface de pareamento.
- `tv`: módulo Android TV mantido para testes/protótipos antigos; **não é necessário para o modo HID**.

## Limitações reais

- O celular precisa suportar `BluetoothHidDevice`.
- O Bluetooth precisa permanecer ligado.
- A manete precisa ser reconhecida pelo Android como gamepad.
- O app precisa permanecer autorizado como serviço de acessibilidade para a captura global.
- O jogo da TV precisa aceitar gamepad.
- Vibração, touchpad e giroscópio ainda não são encaminhados neste primeiro modo HID.
