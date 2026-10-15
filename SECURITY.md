# Política de Segurança

> **Aviso de segurança (15/10/2026):** a chave privada de assinatura do APK e as senhas de release foram versionadas publicamente. Trate o material antigo como comprometido e não o reutilize. A assinatura de release está temporariamente desativada; não instale nem redistribua a versão v2.1.0. A remoção dos arquivos da árvore atual não apaga cópias no histórico Git, em forks, clones ou caches. A release publicada no GitHub ainda precisa ser retirada manualmente. Consulte o [aviso de retirada](dist/README.md) antes de distribuir qualquer artefato.


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
