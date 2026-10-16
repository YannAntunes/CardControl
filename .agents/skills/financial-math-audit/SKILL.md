---
name: financial-math-audit
description: >-
  Audit, implement, and verify financial calculation rules in CardControl. Use this skill when working on installment splitting (splitInstallmentAmounts), billing cycle closing dates (closingDay), statement due dates, or cent-rounding mathematical precision.
---

# Financial Math & Billing Cycles Skill

This skill documents and governs the mathematical and temporal rules implemented in [CardRepository.kt](../../../app/src/main/java/com/cardcontrol/app/data/repository/CardRepository.kt) and verified in [SecurityAndDomainTestHarness.kt](../../../app/src/test/java/com/cardcontrol/app/SecurityAndDomainTestHarness.kt).

---

## 1. Monetary Precision & Split Invariants

### Problem
Dividing amounts by odd installment counts (e.g., R$ 1.000,00 in 7x = R$ 142,8571...) causes floating-point drift or cent mismatches if rounded naively.

### Rule
CardControl mandates:
1. **Canonical Integer Representation (`Long`):** All monetary values are represented as integer cents (`Long`), avoiding IEEE-754 floating point arithmetic issues.
2. **Exact Cent Allocation:** Divide `totalAmount / count` for base cents. Distribute `totalAmount % count` remainder cents evenly (+1 cent to the first `remainder` parcels).
3. **Exact Cent Sum Invariant:** The sum of all installments must **always** equal the original total:
   $$\sum_{i=1}^{N} \text{installment}_i = \text{totalAmount}$$

### Reference Implementation
```kotlin
fun splitInstallmentAmounts(totalAmount: Long, installments: Int): List<Long> {
    val count = installments.coerceIn(1, 48)
    val base = totalAmount / count
    val remainder = (totalAmount % count).toInt()
    return List(count) { index -> if (index < remainder) base + 1L else base }
}
```

---

## 2. Billing Cutoff & Closing Day Logic

### Rule
- **Cutoff Day (`closingDay`):** If a transaction occurs on or after the credit card's statement closing day, it belongs to the **next billing cycle** (month + 1).
- **Year Boundary:** If the purchase is made on or after December's closing day, the first installment is billed in **January of the following year**.
- **Days Range:** `closingDay` and `dueDay` must be strictly between 1 and 28 to safely avoid month-length edge cases (February leap/non-leap years).

### Reference Implementation
```kotlin
fun calculateFirstInstallmentMonth(purchaseDate: LocalDate, closingDay: Int): LocalDate {
    return if (purchaseDate.dayOfMonth >= closingDay) {
        purchaseDate.plusMonths(1).withDayOfMonth(1)
    } else {
        purchaseDate.withDayOfMonth(1)
    }
}
```

---

## 3. Testing Rules

Always assert both:
1. Total preservation (`parcels.sum() == totalAmount`).
2. Correct year increment on year rollover (`LocalDate.of(2026, 12, 15)` with `closingDay = 10` -> `year == 2027, month == 1`).
