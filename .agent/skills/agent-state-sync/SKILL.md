---
name: agent-state-sync
description: >-
  Synchronize CardControl agent governance documents (.agent/CARDCONTROL_STATE.md, .agent/CARDCONTROL_TASKS.yaml, and .agent/CARDCONTROL_EXECPLAN.md) after completing tasks or making design decisions. Use this skill whenever finishing a milestone, updating task statuses, or preparing commit messages.
---

# Agent State & Governance Synchronization Skill

This skill enforces the governance workflow and "Sources of Truth" rules defined in [AGENTS.md](../../../AGENTS.md).

CardControl uses durable files under `.agent/` to maintain continuity across agent sessions and prevent hallucinated progress.

---

## 1. Governance Source Hierarchy

Always consult and update documents in this order:
1. **Most recent user prompt** (Immediate intent)
2. **`.agent/CARDCONTROL_STATE.md`** (Proven runtime state and exact resumption point)
3. **`.agent/CARDCONTROL_TASKS.yaml`** (Task graph and dependencies)
4. **`.agent/CARDCONTROL_EXECPLAN.md`** (Executable milestones and contracts)
5. **`.agent/CARDCONTROL_DECISIONS.md`** (Architectural decision records)

---

## 2. Post-Implementation Synchronization Checklist

Whenever code or architecture changes are made:

### Step 1: Run Verification Gates
Never update state without actual terminal execution:
```powershell
powershell -ExecutionPolicy Bypass -File .agents/skills/cardcontrol-verify/scripts/verify.ps1
```

### Step 2: Update Task Graph (`.agent/CARDCONTROL_TASKS.yaml`)
- Locate the task ID (e.g. `TASK-008`).
- Change `status: "PENDING"` to `status: "COMPLETED"`.
- Record verification method in `verification: "..."`.

### Step 3: Update System State (`.agent/CARDCONTROL_STATE.md`)
- Update `Última atualização` date (DD/MM/YYYY).
- In `Inventário de Módulos e Componentes`, update validation status for affected files.
- In `Evidências dos Gates de Engenharia`, document real execution output (e.g. `7/7 unit tests passed`, `assembleDebug succeeded`).
- Update `Próximos Objetivos Disponíveis no Grafo de Tarefas` to reflect current remaining tasks.

### Step 4: Update Execution Plan (`.agent/CARDCONTROL_EXECPLAN.md`)
- Under `5. Marcos de Entrega`, mark milestone checkboxes `[x]` as appropriate.

### Step 5: Check Consistency
Run the governance audit script:
```powershell
powershell -ExecutionPolicy Bypass -File .agents/skills/agent-state-sync/scripts/check_state.ps1
```

### Step 6: Follow Conventional Commits
Format commit messages with precision:
- `feat:` New capability in app or parsing engine
- `fix:` Bug fix or domain rule correction
- `test:` Unit or integration test coverage addition
- `docs:` Documentation or governance updates
- `chore:` Dependency or wrapper updates
- `GOAL #XX:` Comprehensive engineering milestone delivery
