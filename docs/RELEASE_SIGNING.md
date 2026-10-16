# Procedimento Operacional: Assinatura e Publicação de Releases (v2.3.0+)

## 1. Contexto de Segurança e Revogação

A chave legada anteriormente presente em `app/release.keystore` e as senhas comitadas no histórico inicial foram declaradas como permanentemente comprometidas e revogadas.

Para a versão **v2.2.0 em diante e consolidada na v2.3.0**, o CardControl aplica as seguintes diretrizes inegociáveis:
- Proibição de armazenamento de qualquer `.keystore`, `.jks` ou segredo dentro do repositório Git.
- Gradle configurado para carregar credenciais estritamente via variáveis de ambiente (`CARDCONTROL_KEYSTORE_*`) ou `local.properties` ignorado.
- Workflow do GitHub Actions (`.github/workflows/release.yml`) com validação de segurança que bloqueia a publicação se arquivos de keystore estiverem rastreados pelo Git ou se senhas forem identificadas no código.

---

## 2. Passo a Passo para Gerar Nova Chave e Publicar Release

### Passo 1: Gerar a Keystore de Produção de Forma Segura

Utilize o utilitário incluído no repositório. Ele cria o par de chaves RSA 2048 bits com validade de 10.000 dias, extrai o Fingerprint SHA-256 e gera o Base64 sem salvar credenciais no Git:

**No Windows (PowerShell):**
```powershell
powershell -ExecutionPolicy Bypass -File scripts/generate_release_keystore.ps1
```

**No Linux / macOS (Bash):**
```bash
chmod +x scripts/generate_release_keystore.sh
./scripts/generate_release_keystore.sh
```

> [!IMPORTANT]
> Faça um backup físico e offline seguro do arquivo `cardcontrol-release.jks`. Se essa chave for perdida, não será possível atualizar diretamente instalações pré-existentes.

### Passo 2: Configurar os Segredos no GitHub Actions (Opcional / Recomendado para Chave Fixa)

O pipeline do GitHub Actions suporta dois modos operacionais de assinatura:
1. **Modo Automático Efêmero (Padrão sem secrets):** Caso nenhum segredo esteja cadastrado no repositório, o workflow executa automaticamente `scripts/generate_release_keystore.sh` em tempo de execução no runner seguro, gerando uma keystore RSA 2048 válida e assinando o APK sem bloquear o build.
2. **Modo Chave Persistida (Recomendado):** Caso deseje manter a mesma chave de assinatura entre diferentes versões (permitindo atualizações diretas no Android sem reinstalação), gere a chave via `generate_release_keystore.ps1` / `.sh` e cadastre os 4 Repository Secrets em **Settings → Secrets and variables → Actions**:

| Nome do Segredo | Descrição |
| :--- | :--- |
| `CARDCONTROL_KEYSTORE_BASE64` | Conteúdo do arquivo `.jks` codificado em string Base64 |
| `CARDCONTROL_KEYSTORE_PASSWORD` | Senha de abertura da keystore |
| `CARDCONTROL_KEY_ALIAS` | Alias da chave (padrão: `cardcontrol`) |
| `CARDCONTROL_KEY_PASSWORD` | Senha da chave privada |

### Passo 3: Disparar o Workflow de Release

Com os segredos configurados e os testes passando na branch `main`:

**Opção A: Por Tag Git (Recomendado para versões formais):**
```bash
git tag v2.3.0
git push origin v2.3.0
```

**Opção B: Pelo Painel do GitHub Actions (Disparo Manual):**
1. Acesse a aba **Actions** → selecione o workflow **Release**.
2. Clique em **Run workflow**.
3. Confirme a versão desejada (ex: `v2.3.0`) e dispare.

### Passo 4: Validação do Rascunho (Draft Release)

1. O workflow compila o APK assinado via `./gradlew assembleRelease` e calcula o hash criptográfico SHA-256 no arquivo `SHA256SUMS.txt`.
2. A release é criada no GitHub como **Draft (Rascunho)** para conferência humana.
3. Inspecione o artefato, valide o hash SHA-256 (`apksigner verify --verbose CardControl-v2.3.0.apk`) e publique a release oficial.
