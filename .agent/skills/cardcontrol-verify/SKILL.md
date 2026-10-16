---
name: cardcontrol-verify
description: >-
  Run the CardControl validation gates (unit tests and debug APK compilation) in accordance with project acceptance rules. Use this skill whenever code is modified, before declaring tasks complete, or when validating build integrity.
---

# CardControl Verification & Build Gates

This skill guides the execution of the mandatory acceptance gates defined in [AGENTS.md](../../../AGENTS.md).

No feature, bug fix, or refactor in CardControl can be considered complete without real execution evidence from these gates.

---

## 1. Fast Validation via Helper Script

The quickest way to run the full verification harness is using the bundled PowerShell script:

```powershell
powershell -ExecutionPolicy Bypass -File .agents/skills/cardcontrol-verify/scripts/verify.ps1
```

### Options:
- **Unit tests only:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .agents/skills/cardcontrol-verify/scripts/verify.ps1 -TestsOnly
  ```
- **Debug build assembly only:**
  ```powershell
  powershell -ExecutionPolicy Bypass -File .agents/skills/cardcontrol-verify/scripts/verify.ps1 -AssembleOnly
  ```

---

## 2. Direct Gradle Commands

If running manually in PowerShell:

### Step 1: Set JDK (Java 17 / 21)
```powershell
if (Test-Path "C:\Program Files\Java\jdk-17") { $env:JAVA_HOME="C:\Program Files\Java\jdk-17" }
elseif (Test-Path "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot") { $env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot" }
```

### Step 2: Unit Test Suite
```powershell
.\gradlew.bat testDebugUnitTest --console=plain "-Pkotlin.compiler.execution.strategy=in-process"
```
- Must run all tests in `SecurityAndDomainTestHarness`.
- Zero failures, zero errors required.

### Step 3: Debug APK Compilation
```powershell
.\gradlew.bat assembleDebug --console=plain --max-workers=2 "-Pkotlin.compiler.execution.strategy=in-process"
```
- Produces: `app/build/outputs/apk/debug/app-debug.apk`
- Verifies Compose UI compiler and Room KSP code generation.

---

## 3. Critical Rules and Constraints

- **Never Alucinate:** Distinguish clearly between code inspected, hypothesis, executed test, and observed behavior. Never report tests passing without actual terminal execution.
- **Release Signing Ban:** Do **NOT** run `assembleRelease` until the rotated signing key and migration strategy are officially established (as recorded in `.agent/CARDCONTROL_STATE.md`).
- **Offline Invariant:** Verification must succeed in an offline environment without external cloud dependencies.
