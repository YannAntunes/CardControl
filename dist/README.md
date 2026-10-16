# Histórico de Distribuição e Integridade de Binários

## 1. Status da Versão v2.1.0 (Revogada)

O APK correspondente à versão **v2.1.0 foi permanentemente retirado e descontinuado**.

Em 15/10/2026, foi detectado que a chave privada de assinatura utilizada naquele ciclo foi versionada publicamente no histórico inicial do repositório. Por diretriz de segurança estrita, todo o material criptográfico antigo foi invalidado. Nenhum artefato assinado com aquela chave deve ser executado ou redistribuído.

## 2. Canal Oficial de Distribuição (v2.2.0 e v2.3.0+)

A partir da versão **v2.2.0 e consolidada na v2.3.0**, o CardControl adota o seguinte modelo seguro de publicação:

1. **Repositório Git Limpo e Livre de Binários:** Nenhum executável compilado (`.apk`, `.aab`) deve residir na árvore de diretórios do Git ou solto na raiz do repositório. O diretório `dist/` é mantido exclusivamente para documentação de integridade.
2. **Releases Oficiais:** Os lançamentos autenticados são publicados na aba oficial de [Releases do GitHub](https://github.com/kauankelvin7/CardControl/releases).
3. **Assinatura Persistente:** A compilação de produção exige os quatro secrets de assinatura e a variável `CARDCONTROL_CERT_SHA256`. O workflow não gera chaves temporárias e verifica o certificado da keystore e do APK.
4. **Transição de Assinatura:** O responsável confirmou que nenhum dispositivo instalou o APK assinado com a chave exposta e que a release foi retirada. A versão 2.3.0 usará uma nova keystore persistente. Defina `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true` para registrar a decisão; a publicação segue sujeita à validação do workflow e revisão humana.
5. **Verificação de Checksum:** Cada release é acompanhada por um arquivo `SHA256SUMS.txt` gerado diretamente na máquina de compilação para conferência contra adulterações.
