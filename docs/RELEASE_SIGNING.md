# Assinatura e Publicação de Releases

## Situação de segurança

A chave de release anterior e as senhas que chegaram ao histórico público são consideradas comprometidas. A nova chave precisa ficar fora do Git e ser preservada em backup offline.

Decisão de assinatura: o responsável confirmou que nenhum dispositivo instalou APK assinado com a chave anterior e que a release foi retirada. A versão 2.3.0 usará uma nova keystore persistente, fora do Git; a identidade antiga não será reutilizada. Essa decisão dispensa preservar atualização in-place de instalações com o certificado anterior, desde que a confirmação esteja correta. O workflow exige os quatro secrets, o fingerprint confiável e CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true, e gera somente uma Draft Release para revisão humana.

## Inicialização única

Escolha um computador confiável para manter a keystore e guarde um backup offline seguro. O gerador não sobrescreve keystores existentes e, por padrão, salva os arquivos fora do repositório.

### Windows

Com o GitHub CLI instalado e autenticado (`gh auth login`), execute a partir da raiz do repositório somente depois de aprovar a estratégia de atualização:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate_release_keystore.ps1 -AcknowledgeUpgradeCompatibility -SetGitHubSecrets
```

### Linux/macOS

```bash
chmod +x scripts/generate_release_keystore.sh
./scripts/generate_release_keystore.sh --acknowledge-upgrade-compatibility --set-github-secrets
```

Os scripts criam uma keystore persistente, cadastram os quatro secrets e definem as variables CARDCONTROL_CERT_SHA256 e CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true. A configuracao so e considerada concluida quando o GitHub confirma os quatro secrets e as duas variables.

## Provisionar a chave persistente ja criada

Como a keystore desta configuracao ja foi criada em `CardControlSigning`, nao execute novamente o gerador. Depois de autenticar `gh auth login --web`, execute:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/provision_release_secrets.ps1 -AcknowledgeNoExistingInstallations
```

O script valida que o arquivo local de credenciais corresponde exatamente a keystore, calcula o fingerprint, envia os secrets via stdin sem imprimir valores e verifica os nomes e variables de volta no GitHub. Se a configuracao remota existente precisar ser substituida pela nova chave, acrescente `-ReplaceExistingConfiguration`; isso atualiza os quatro signing secrets e as duas variables para a mesma identidade nova. Nenhuma release e publicada.## Cadastro manual

Se não for usar o GitHub CLI, execute o gerador com a confirmação explícita de compatibilidade, mas sem `-SetGitHubSecrets` no Windows ou sem `--set-github-secrets` no Linux/macOS. Faça isso somente depois da avaliação estar concluída. Use o arquivo local protegido `release-secrets.env` para cadastrar os seguintes **Repository Secrets** em [Settings → Secrets and variables → Actions](https://github.com/kauankelvin7/CardControl/settings/secrets/actions):

| Nome | Conteúdo |
| --- | --- |
| `CARDCONTROL_KEYSTORE_BASE64` | Keystore em Base64 |
| `CARDCONTROL_KEYSTORE_PASSWORD` | Senha da keystore |
| `CARDCONTROL_KEY_ALIAS` | Alias usado na chave |
| `CARDCONTROL_KEY_PASSWORD` | Senha da chave privada |

No Windows, use `powershell -ExecutionPolicy Bypass -File scripts/generate_release_keystore.ps1 -AcknowledgeUpgradeCompatibility`; no Linux/macOS, use `./scripts/generate_release_keystore.sh --acknowledge-upgrade-compatibility`. Na seção Variables, configure `CARDCONTROL_CERT_SHA256` com o fingerprint exibido pelo gerador e `CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true`, pois o responsável confirmou que nenhum dispositivo instalou o APK anterior. O fingerprint não é segredo, mas funciona como valor de confiança para o workflow.

Não copie os valores secretos para o código, logs, issues ou commits. Proteja o arquivo local com credenciais e permissões do usuário, e mantenha uma cópia offline da keystore e das senhas.

## Verificações automáticas

O workflow de release falha se algum dos quatro secrets ou a variável do fingerprint estiver ausente, se a keystore/alias/senha não abrir corretamente, se o certificado não corresponder ao fingerprint confiável, ou se a assinatura do APK final não corresponder ao mesmo certificado. Não existe fallback para chave temporária.

Depois de passar por essas verificações, o workflow cria somente uma **Draft Release** e anexa o APK e seu `SHA256SUMS.txt`. Verifique o artefato antes de qualquer publicação oficial.
