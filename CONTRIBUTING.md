# Trabalhar no CardControl

Este documento estabelece o fluxo de desenvolvimento, validação e regras de commit para contribuidores e agentes de IA, adaptado dos padrões do repositório de referência *Leve*.

---

## 1. Ambiente e Ferramentas

- **JDK:** OpenJDK 17 fixado para o compilador Kotlin e Gradle.
- **Android SDK:** API 34 (Android 14) com `build-tools 34.0.0` e `platform-tools`.
- **Gradle:** Wrapper versão 8.7 (`gradlew` / `gradlew.bat`).
- **Gradle Daemon:** Para compilação em máquinas com recursos moderados de memória, utilize a flag `-Pkotlin.compiler.execution.strategy=in-process` e `--max-workers=2`.

---

## 2. Checklist Obrigatório de Verificação (Gates)

Antes de propor alterações ou commitar:

1. **Testes Unitários:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Todas as 7 asserções da suíte `SecurityAndDomainTestHarness` devem passar.

2. **Compilação do APK de Release:**
   ```bash
   ./gradlew assembleRelease
   ```
   Certifique-se de que a geração de classes do Room via KSP e a minificação passem sem erros.

3. **Verificação de Assinatura:**
   ```bash
   apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
   ```
   O APK deve conter assinatura válida v2 do certificado do projeto.

---

## 3. Padrão de Commits

Adotamos a convenção **Conventional Commits**:

- `feat:` Nova funcionalidade no app ou motor de parsing.
- `fix:` Correção de bug ou regra de negócio.
- `test:` Inclusão ou ajuste de testes unitários e de integração.
- `docs:` Atualização de documentação técnica ou manuais.
- `ci:` Alteração em pipelines de integração contínua (GitHub Actions).
- `chore:` Atualizações de dependências, wrappers ou configurações.
- `GOAL #XX:` Entregas abrangentes de engenharia com marcos executáveis.

### Regras Estritas de Segurança nos Commits:
- Nunca faça commits com segredos, tokens ou senhas reais.
- Mantenha o arquivo `local.properties` fora do versionamento (`.gitignore`).
- Certificados e senhas de teste devem ser declarados com clareza nos manifestos.

---

## 4. Contratos de Código

- **Offline Estrito:** Não crie writers ou conectores para servidores de terceiros.
- **Deduplicação:** Toda notificação deve passar pelo cálculo de hash SHA-256 antes da persistência.
- **Sanitização:** Entradas de texto em faturas ou relatórios devem ser higienizadas contra CRLF injection e Path Traversal.
