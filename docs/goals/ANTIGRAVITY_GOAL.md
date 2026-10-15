# GOAL: Implantação, Testes e Build do CardControl v2.1

## Contexto
Projeto Android nativo (Kotlin + Jetpack Compose + Room + Keystore) 100% offline-first.

## Etapas de Execução Obrigatórias
1. **Ambiente e Wrapper:** Verifique o `ANDROID_HOME` (API 34) e JDK 17. Caso não exista `gradlew`, gere com `gradle wrapper --gradle-version 8.7`.
2. **Harness de Segurança e Domínio:** Execute `./gradlew testDebugUnitTest` e valide todas as asserções da suíte `SecurityAndDomainTestHarness`.
3. **Compilação do Binário:** Execute `./gradlew assembleDebug` e confirme a geração de `app/build/outputs/apk/debug/app-debug.apk`.
4. **Relatório:** Apresente o resultado real do terminal para os testes e build, sem suposições.
