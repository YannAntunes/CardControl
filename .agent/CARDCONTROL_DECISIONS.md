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
- **Status:** Aceito
- **Contexto:** Builds `debug` usam chaves transitórias que acionam avisos agressivos de segurança no Android e quebram a compatibilidade de atualização em sideload.
- **Decisão:** Criar um `release.keystore` de 2048-bit com validade de 25 anos em nome de `CN=CardControl`, fixando o esquema APK Signature Scheme v2 para permitir atualizações diretas no Android sem perda do banco SQLite.
