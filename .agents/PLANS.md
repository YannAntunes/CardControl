# Execution Plans

Para funcionalidades complexas, refatorações de regras de cálculo ou tarefas que toquem múltiplos módulos (parser, banco e UI), use um ExecPlan persistido.

Um ExecPlan deve permitir que qualquer agente ou desenvolvedor retome o trabalho sem contexto prévio, apenas inspecionando o repositório.

## Seções Obrigatórias

1. **Objetivo:** O comportamento observável a ser entregue.
2. **Contexto Atual:** Arquivos relevantes, contratos e restrições descobertas.
3. **Não Objetivos:** O que fica explicitamente fora do escopo.
4. **Contratos:** Entidades, queries, interfaces ou eventos afetados.
5. **Passos:** Tarefas pequenas, ordenadas e verificáveis.
6. **Ownership:** Arquivos de responsabilidade de cada etapa.
7. **Gates:** Comandos de terminal obrigatórios para aprovação.
8. **Rollback:** Procedimento para reversão segura.
9. **Evidências:** Logs reais de compilação ou execução de testes.
10. **Estado de Retomada:** Ponto exato para o próximo passo.
