# Modelo de Ameaças e Mitigações de Segurança

O CardControl processa dados sensíveis locais de compras e faturas. Este documento formaliza os vetores de ataque avaliados e as mitigações implementadas no código.

---

## 1. Vetores de Ataque e Controles

| Vetor de Ataque | Cenário Potencial | Mitigação Implementada no CardControl |
| :--- | :--- | :--- |
| **CRLF Injection em SMTP** | Estabelecimento malicioso contendo `\r\nBcc: hacker@site.com` injeta destinatários ocultos. | `SecuritySanitizer.sanitizeHeaderField` substitui sequências `[\r\n\t]+` por espaços e trunca em 80 caracteres. |
| **Path Traversal em Cache PDF** | Titular cadastrado com nome `../../etc/passwd` tenta gravar PDFs fora da pasta de cache do app. | `SecuritySanitizer.sanitizeFileNameSegment` elimina caracteres proibidos e o código valida `file.canonicalPath.startsWith(dir.canonicalPath)`. |
| **ReDoS (Regular Expression DoS)** | Notificação maliciosa com payload de 100.000 caracteres trava a CPU do aparelho em backtracking de Regex. | `SecuritySanitizer.boundNotificationInput` trunca o texto para 1.000 caracteres antes de aplicar qualquer expressão regular. |
| **Exfiltração de Dados Financeiros** | Telemetria oculta ou SDKs de terceiros vazando gastos de faturas. | Zero bibliotecas de analytics ou rastreamento. O app opera em modo 100% offline-first sem endpoint de rede próprio. |
| **Roubo de Credenciais SMTP** | Leitura de senhas de e-mail por backup não autorizado ou depuração de memória. | Criptografia em hardware via `EncryptedSharedPreferences` amarrada ao Android Keystore com `allowBackup="false"`. |

---

## 2. Cobertura de Testes Automatizados

A suíte `SecurityAndDomainTestHarness.kt` valida continuamente em tempo de build:
- Bloqueio de Path Traversal em nomes de titulares;
- Neutralização de injeção CRLF;
- Truncamento e proteção contra ReDoS;
- Rejeição de pacotes não bancários ou forjados.
