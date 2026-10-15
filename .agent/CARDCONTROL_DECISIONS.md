# Registros de Decisões Arquiteturais (ADRs)

## ADR-001: Persistência Local Estrita com Room SQLite
- **Status:** Aceito
- **Contexto:** Necessidade de garantir privacidade financeira absoluta, sem custos de servidor e funcionamento sem internet.
- **Decisão:** Utilizar SQLite local gerenciado pela biblioteca Jetpack Room com migration destrutiva em desenvolvimento e chaves estrangeiras com `CASCADE`.

---

## ADR-002: Captura Passiva de Notificações via `NotificationListenerService`
- **Status:** Aceito
- **Contexto:** O Android proíbe leitura direta de SMS bancários em versões recentes e apps bancários não fornecem APIs locais públicas gratuitas.
- **Decisão:** Implementar `NotificationListenerService` oficial do Android. O app reage a eventos emitidos pelos bancos, filtrando palavras-chave de aprovação e estorno, sem manter loops ou polling que drenem bateria.

---

## ADR-003: Criptografia de Credenciais no Android Keystore
- **Status:** Aceito
- **Contexto:** Se o usuário optar por alertas de e-mail instantâneos via SMTP, a senha de aplicativo não pode ficar legível no disco.
- **Decisão:** Usar `EncryptedSharedPreferences` com cifra `AES256-GCM` baseada em hardware através do `MasterKey`.

---

## ADR-004: Design System Próprio em Jetpack Compose ("Liquid Glass")
- **Status:** Aceito
- **Contexto:** O visual padrão do Material Design 3 é genérico. O usuário solicitou estética visual de alto nível.
- **Decisão:** Criar um tema personalizado baseado em vidro líquido (*Liquid Glass*), com paleta *Obsidian/DeepSlate*, gradientes suaves, ambient glow ciano/índigo e componentes sob medida (`GlassTextField`, `LiquidPillSelector`, `LiquidGlassBar`).

---

## ADR-005: Certificado de Assinatura Local Próprio (Keystore Dedicado)
- **Status:** Suspenso por incidente de segurança (15/10/2026)
- **Contexto:** O keystore de release e as senhas associadas foram versionados em um repositório público. O material deve ser tratado como comprometido.
- **Decisão anterior:** Um keystore dedicado era usado para assinar APKs distribuídos diretamente e permitir atualizações com a mesma assinatura.
- **Decisão atual:** Não reutilizar a chave nem as senhas expostas. A assinatura de release está desativada até que o caminho de atualização das instalações existentes seja avaliado, uma nova chave seja gerada e a assinatura passe a usar configuração externa ao Git. Uma nova chave pode impedir a atualização direta de instalações assinadas com a chave anterior; não publicar uma versão até validar a estratégia de migração.
