# Política de Segurança

A segurança do **CardControl** é baseada em três pilares fundamentais:
1. **Modelo 100% Offline-First:** Ausência de servidores remotos, telemetria ou envio de dados financeiros para nuvem.
2. **Criptografia de Hardware:** Cifras `AES256-GCM` gerenciadas diretamente pelo Android Keystore.
3. **Defesa em Profundidade contra Injeções:** Higienização de strings de entrada e validação matemática de limites.

---

## 1. Escopo de Controles Técnicos

- **Sanitização de Cabeçalhos:** Eliminação de quebras de linha (`\r`, `\n`) em nomes de lojas e titulares para impedir injeção de cabeçalhos SMTP CRLF.
- **Prevenção de Path Traversal:** Remoção de sequências como `../` e caracteres proibidos ao gerar nomes de arquivos PDF em cache.
- **Proteção contra ReDoS:** Truncamento de notificações gigantes para até 1.000 caracteres antes da aplicação de expressões regulares.
- **Validação de Limites Monetários:** Rejeição de valores negativos, infinitos, NaN ou superiores a R$ 1.000.000,00 por transação.

---

## 2. Como Relatar Vulnerabilidades

Caso identifique qualquer falha de segurança que possa comprometer a integridade local do aplicativo:

- Não abra issues públicas com detalhes de exploração.
- Utilize o recurso privado **Security → Report a vulnerability** no repositório GitHub.
- Forneça os passos de reprodução com dados sintéticos ou fictícios.
