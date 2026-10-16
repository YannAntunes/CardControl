# Histórico de Distribuição e Integridade de Binários

## 1. Status da Versão v2.1.0 (Revogada)

O APK correspondente à versão **v2.1.0 foi permanentemente retirado e descontinuado**.

Em 15/10/2026, foi detectado que a chave privada de assinatura utilizada naquele ciclo foi versionada publicamente no histórico inicial do repositório. Por diretriz de segurança estrita, todo o material criptográfico antigo foi invalidado. Nenhum artefato assinado com aquela chave deve ser executado ou redistribuído.

## 2. Canal Oficial de Distribuição (v2.2.0 e v2.3.0+)

A partir da versão **v2.2.0 e consolidada na v2.3.0**, o CardControl adota o seguinte modelo seguro de publicação:

1. **Repositório Git Limpo e Livre de Binários:** Nenhum executável compilado (`.apk`, `.aab`) deve residir na árvore de diretórios do Git ou solto na raiz do repositório. O diretório `dist/` é mantido exclusivamente para documentação de integridade.
2. **Releases Oficiais:** Os lançamentos autenticados são publicados na aba oficial de [Releases do GitHub](https://github.com/kauankelvin7/CardControl/releases).
3. **Assinatura Isolada e Flexível:** A compilação e assinatura de produção são executadas pelo pipeline automatizado do GitHub Actions via segredos criptografados de repositório (`CARDCONTROL_KEYSTORE_*`) ou geração automática segura a cada release via `scripts/generate_release_keystore.sh`.
4. **Verificação de Checksum:** Cada release é acompanhada por um arquivo `SHA256SUMS.txt` gerado diretamente na máquina de compilação para conferência contra adulterações.
