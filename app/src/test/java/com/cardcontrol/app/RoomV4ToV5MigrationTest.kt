package com.cardcontrol.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.cardcontrol.app.data.local.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class RoomV4ToV5MigrationTest {

    @Test
    fun `migration 4 para 5 deve adicionar coluna isRecurring e criar tabela recurring_charges com indices`() {
        val executedSqls = mutableListOf<String>()

        val handler = InvocationHandler { _: Any?, method: Method, args: Array<out Any?>? ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        }

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            handler
        ) as SupportSQLiteDatabase

        AppDatabase.MIGRATION_4_5.migrate(dbProxy)

        // 1. Deve alterar tabela transactions para adicionar isRecurring
        assertTrue(
            "Deve adicionar coluna isRecurring na tabela transactions",
            executedSqls.any { it.contains("ALTER TABLE `transactions` ADD COLUMN `isRecurring` INTEGER NOT NULL DEFAULT 0") }
        )

        // 2. Deve criar a tabela recurring_charges com chaves estrangeiras
        assertTrue(
            "Deve criar tabela recurring_charges",
            executedSqls.any { it.contains("CREATE TABLE IF NOT EXISTS `recurring_charges`") && it.contains("expectedAmount") && it.contains("lastAmount") }
        )

        // 3. Deve criar índices de busca em cardId, holderId e merchant
        assertTrue(
            "Deve criar índice em cardId",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_recurring_charges_cardId`") }
        )
        assertTrue(
            "Deve criar índice em holderId",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_recurring_charges_holderId`") }
        )
        assertTrue(
            "Deve criar índice em merchant",
            executedSqls.any { it.contains("CREATE INDEX IF NOT EXISTS `index_recurring_charges_merchant`") }
        )
    }
}
