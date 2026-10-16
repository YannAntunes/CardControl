# Política de Segurança — CardControl

> **Status de Segurança (v2.2.0):** Em 15/10/2026, foi identificado que a chave privada de assinatura e senhas da versão legada v2.1.0 foram versionadas publicamente no histórico inicial do repositório. O material antigo foi permanentemente revogado e o binário v2.1.0 descontinuado. A partir da versão **v2.2.0**, o processo de compilação de release foi totalmente reformulado: o código não armazena chaves nem senhas, o pipeline de CI do GitHub Actions exige segredos rotacionados isolados e bloqueia compilações caso qualquer chave seja detectada no Git.

---

## 1. Pilares Fundamentais de Arquitetura

1. **Modelo 100% Offline-First:** Ausência absoluta de servidores intermediários, telemetria, anúncios ou sincronização em nuvem. Todos os dados financeiros residem estritamente no banco SQLite privado do aparelho (`card_control_offline.db`).
2. **Criptografia em Hardware:** O cofre de credenciais SMTP opcionais utiliza cifras `AES256-GCM` gerenciadas diretamente pelo chip de hardware via Android Keystore (`EncryptedSharedPreferences`).
3. **Precisão Contábil Canônica:** Todas as grandezas financeiras são manipuladas e persistidas como centavos inteiros (`Long`), eliminando vulnerabilidades e imprecisões decorrentes de arredondamentos de ponto flutuante.
4. **Defesa em Profundidade contra Injeções:** Higienização de strings de entrada, deduplicação criptográfica e validação matemática de limites.

---

## 2. Escopo de Controles Técnicos Auditados

- **Sanitização de Cabeçalhos (Anti-CRLF):** Eliminação de quebras de linha (`\r`, `\n`) em nomes de lojas e titulares (`SecuritySanitizer.sanitizeHeaderField`) para impedir injeção de cabeçalhos SMTP CRLF.
- **Prevenção de Path Traversal:** Remoção de sequências maliciosas como `../` e caracteres proibidos (`SecuritySanitizer.sanitizeFileNameSegment`) ao gerar nomes de arquivos PDF em cache.
- **Proteção contra ReDoS:** Truncamento de notificações push locais para no máximo 1.000 caracteres antes da aplicação de expressões regulares de extração bancária.
- **Atomicidade e Concorrência:** Sincronização atômica de inserção de compras e resolução de cartões através do `CardRepository.expenseMutex` estático (`companion object`), prevenindo duplicação em concorrência multi-instância na mesma JVM.
- **Deduplicação Criptográfica Temporal:** Geração de hash SHA-256 combinando pacote bancário, valor em centavos, estabelecimento e final do cartão com janela de tolerância de 120 segundos.
- **Salvaguardas de Release no CI/CD:** O workflow de release do GitHub Actions (`.github/workflows/release.yml`) verifica compulsoriamente:
  - Ausência de arquivos `.keystore` ou `.jks` rastreados pelo Git.
  - Ausência de senhas em texto puro nos arquivos Gradle.
  - Presença dos 4 segredos protegidos no repositório (`CARDCONTROL_KEYSTORE_*`).
  - Geração e publicação de somas de verificação SHA-256 (`SHA256SUMS.txt`).

---

## 3. Como Relatar Vulnerabilidades

Caso identifique qualquer falha de segurança que possa comprometer a integridade local do aplicativo:

- Não abra issues públicas com detalhes de exploração.
- Utilize o recurso privado **Security → Report a vulnerability** no repositório GitHub.
- Forneça os passos de reprodução com dados sintéticos ou fictícios.
- A equipe de mantenedores responderá em até 48 horas úteis com triagem e plano de remediação.
