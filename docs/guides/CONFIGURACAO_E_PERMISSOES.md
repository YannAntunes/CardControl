# Guia de Configuração e Permissões

Este guia orienta o usuário passo a passo sobre como habilitar as permissões necessárias para o funcionamento 100% autônomo do CardControl no Android 13 e 14+.

---

## 1. Desbloqueio de Configurações Restritas (Android 13 e 14)

Nos aparelhos com Android 13 ou superior, o sistema operacional bloqueia por padrão permissões sensíveis (como Acesso a Notificações) para qualquer app instalado manualmente fora da Google Play Store (*sideload*).

Ao tentar ativar a chave no app, o Android exibe a mensagem:
> *"Para sua segurança, esta configuração não está disponível no momento."*

### Como liberar (leva 15 segundos):
1. No celular, abra as **Configurações** do Android (ícone de engrenagem).
2. Vá em **Aplicativos** (ou **Apps**) e localize o **CardControl** na lista.
3. No canto superior direito da tela de informações do CardControl, toque no menu de **3 pontinhos verticais (⋮)**.
   *(Nota: em aparelhos Xiaomi/MIUI/HyperOS, essa opção fica dentro de "Permissões avançadas" ou no rodapé).*
4. Toque em **"Permitir configurações restritas"** (*Allow restricted settings*).
5. O sistema solicitará sua **digital ou senha de desbloqueio** para confirmar que foi você quem autorizou.
6. Volte ao CardControl: a tela de **Acesso a notificações** agora estará destravada e a chave poderá ser ativada normalmente.

---

## 2. Configuração dos Apps Bancários

Para que o CardControl intercepte as compras em tempo real, certifique-se de que os aplicativos dos seus bancos estejam configurados para emitir notificações no aparelho:

1. No aplicativo do seu banco (Nubank, Inter, Itaú, C6, etc.), certifique-se de que a opção de **Notificações de compras no cartão** esteja ligada.
2. Nas configurações do Android, verifique se a economia de bateria agressiva do sistema não está suspendendo o CardControl em segundo plano (coloque o app em modo de bateria *Sem restrições* ou *Otimizado padrão*).

---

## 3. Configuração do Envio de Alertas por E-mail (Opcional)

Se desejar que o CardControl envie comprovantes instantâneos por e-mail para os dependentes quando uma compra for realizada:
1. Acesse sua conta Google e gere uma **Senha de Aplicativo** de 16 caracteres em: [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
2. Abra a aba **Automação** no CardControl.
3. Insira seu e-mail do Gmail e a senha de app de 16 caracteres.
4. Toque em **Criptografar no Android Keystore**. Suas credenciais serão salvas de forma cifrada no chip de segurança do aparelho.
