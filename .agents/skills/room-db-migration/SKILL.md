---
name: room-db-migration
description: >-
  Manage Room SQLite entities, DAOs, schema migrations, and offline-first database evolution in CardControl. Use this skill when modifying local entities (Entities.kt), adding queries (AppDao.kt), bumping AppDatabase version, or writing Room migrations.
---

# Room Database Evolution & Migration Skill

This skill guides safely evolving the private local SQLite database `card_control_offline.db` in [AppDatabase.kt](../../../app/src/main/java/com/cardcontrol/app/data/local/AppDatabase.kt).

---

## 1. Cardinal Invariants

1. **100% Offline-First:** Zero network calls or cloud synchronizers. The database lives entirely within the app's sandboxed private storage.
2. **Foreign Key Indices:** Every `ForeignKey` column (`defaultHolderId`, `cardId`, `holderId`, `transactionId`) **must** have an `@Index` declaration in `@Entity(indices = [...])` to prevent full table scans and Room KSP compile warnings.
3. **Monetary Precision:** Store amounts as `Double` representing BRL currency, calculated and split through `BigDecimal` in `CardRepository.kt`.

---

## 2. Step-by-Step Procedure

### Step 1: Modify Entity or Add Table
File: [Entities.kt](../../../app/src/main/java/com/cardcontrol/app/data/local/Entities.kt)
- Always provide sane default values for new columns (e.g. `val isArchived: Boolean = false`).
- Keep primary keys auto-generated: `@PrimaryKey(autoGenerate = true) val id: Long = 0`.

### Step 2: Update AppDao Queries
File: [AppDao.kt](../../../app/src/main/java/com/cardcontrol/app/data/local/AppDao.kt)
- Use reactive `Flow<List<T>>` for UI observers (`observe...`).
- Use `suspend fun` for mutations (`insert...`, `delete...`, `update...`).
- Keep queries parameterized (`:holderId`, `:month`, `:year`) to prevent SQL injection.

### Step 3: Increment Version & Add Migration
File: [AppDatabase.kt](../../../app/src/main/java/com/cardcontrol/app/data/local/AppDatabase.kt)
- Increment `version = X + 1`.
- If preserving user data across updates, register a `Migration`:
  ```kotlin
  val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE holders ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
      }
  }
  ```
  Add to builder: `.addMigrations(MIGRATION_2_3)` before `.fallbackToDestructiveMigration()`.

### Step 4: Verify KSP and Build
Run the validation gate to trigger KSP code generation and verify that all schemas and DAO queries compile:
```powershell
powershell -ExecutionPolicy Bypass -File .agents/skills/cardcontrol-verify/scripts/verify.ps1 -TestsOnly
```
