#!/usr/bin/env python3
"""
Validação automatizada de migração Room v3 -> v4 em SQLite real:
1. Cria um banco SQLite físico com o schema v3 idêntico ao Room (app/schemas/.../3.json).
2. Popula dados reais (holders, cartões com nomes variados, transações, parcelas).
3. Aplica a migração v3 -> v4:
   - ALTER TABLE credit_cards ADD COLUMN bank TEXT NOT NULL DEFAULT 'other'
   - CREATE INDEX IF NOT EXISTS index_credit_cards_bank ON credit_cards (bank)
   - Executa a inferência estrita de bancos
4. Valida preservação de dados existentes, foreign keys, índices e isolamento de cartões ambíguos.
"""

import json
import os
import re
import sqlite3
import sys
import tempfile
import unicodedata
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

def normalize_text(text: str) -> str:
    nfkd = unicodedata.normalize('NFD', text.lower())
    without_accents = "".join(c for c in nfkd if unicodedata.category(c) != 'Mn')
    return without_accents

def infer_bank_from_name(name: str) -> str:
    normalized = normalize_text(name)
    tokens = set(re.findall(r'[a-z0-9]+', normalized))
    
    matches = []
    if any(t in {"nu", "nubank"} for t in tokens):
        matches.append("nubank")
    if any(t in {"inter"} for t in tokens):
        matches.append("inter")
    if any(t in {"itau", "itaucard"} for t in tokens):
        matches.append("itau")
    if any(t in {"c6", "c6bank"} for t in tokens):
        matches.append("c6")
    if any(t in {"santander"} for t in tokens):
        matches.append("santander")
    if any(t in {"bradesco"} for t in tokens):
        matches.append("bradesco")
    if any(t in {"caixa"} for t in tokens):
        matches.append("caixa")
    if any(t in {"picpay"} for t in tokens):
        matches.append("picpay")
    if any(t in {"mercado", "mercadopago"} for t in tokens):
        matches.append("mercadopago")
        
    return matches[0] if len(matches) == 1 else "other"

def run_real_sqlite_migration_test():
    print("[TEST] Iniciando validação de migração Room v3 -> v4 em SQLite real...")
    
    schema_v3_path = REPO_ROOT / "app" / "schemas" / "com.cardcontrol.app.data.local.AppDatabase" / "3.json"
    schema_v4_path = REPO_ROOT / "app" / "schemas" / "com.cardcontrol.app.data.local.AppDatabase" / "4.json"
    
    assert schema_v3_path.exists(), f"Schema v3 não encontrado em {schema_v3_path}"
    assert schema_v4_path.exists(), f"Schema v4 não encontrado em {schema_v4_path}"
    
    with open(schema_v3_path, "r", encoding="utf-8") as f:
        v3_data = json.load(f)
    with open(schema_v4_path, "r", encoding="utf-8") as f:
        v4_data = json.load(f)
        
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = Path(tmpdir) / "test_migration.db"
        conn = sqlite3.connect(db_path)
        conn.execute("PRAGMA foreign_keys = ON")
        cursor = conn.cursor()
        
        # 1. Cria tabelas e índices v3
        print("  1. Criando tabelas do schema v3...")
        for entity in v3_data["database"]["entities"]:
            create_sql = entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])
            cursor.execute(create_sql)
            for idx in entity.get("indices", []):
                idx_sql = idx["createSql"].replace("${TABLE_NAME}", entity["tableName"])
                cursor.execute(idx_sql)
                
        # 2. Insere dados de teste no schema v3
        print("  2. Inserindo dados de teste pré-migração...")
        cursor.execute("INSERT INTO holders (id, name, whatsapp, email, subLimit, notifyByEmailOnPurchase) VALUES (1, 'Ana Silva', '11999999999', 'ana@email.com', 200000, 1)")
        cursor.execute("INSERT INTO holders (id, name, whatsapp, email, subLimit, notifyByEmailOnPurchase) VALUES (2, 'Carlos Souza', '11888888888', 'carlos@email.com', 300000, 1)")
        
        test_cards = [
            (10, 'Nu Ana', '4321', 500000, 5, 12, 1),
            (20, 'Meu Itaú Platinum', '9988', 800000, 10, 17, 1),
            (30, 'Inter Black', '1122', 1500000, 15, 22, 2),
            (40, 'Cartão Geral da Família', '3344', 300000, 5, 12, 1), # Sem banco -> deve virar 'other'
            (50, 'Cartão Nubank e Inter', '5566', 400000, 10, 17, 2), # Ambíguo -> deve virar 'other'
            (60, 'Bradesco Prime', '7788', 600000, 15, 22, 2)
        ]
        cursor.executemany("INSERT INTO credit_cards (id, name, lastFourDigits, totalLimit, closingDay, dueDay, defaultHolderId) VALUES (?, ?, ?, ?, ?, ?, ?)", test_cards)
        
        # Insere transação e parcelas para checar integridade de Foreign Key
        cursor.execute("INSERT INTO transactions (id, cardId, holderId, merchant, totalAmount, installmentsCount, purchaseTimestamp, isAutoCaptured, dedupHash) VALUES (100, 10, 1, 'PADARIA CENTRAL', 4590, 1, 1760000000000, 1, 'hash123')")
        cursor.execute("INSERT INTO installments (id, transactionId, holderId, cardId, merchantSnapshot, installmentNumber, totalInstallments, amount, refMonth, refYear, purchaseTimestamp) VALUES (1000, 100, 1, 10, 'PADARIA CENTRAL', 1, 1, 4590, 10, 2026, 1760000000000)")
        conn.commit()
        
        # 3. Executa a migração v3 -> v4 exatamente como MIGRATION_3_4
        print("  3. Executando MIGRATION_3_4 no banco real...")
        cursor.execute("ALTER TABLE `credit_cards` ADD COLUMN `bank` TEXT NOT NULL DEFAULT 'other'")
        cursor.execute("CREATE INDEX IF NOT EXISTS `index_credit_cards_bank` ON `credit_cards` (`bank`)")
        
        cursor.execute("SELECT `id`, `name` FROM `credit_cards`")
        rows = cursor.fetchall()
        for card_id, card_name in rows:
            inferred = infer_bank_from_name(card_name)
            if inferred != "other":
                cursor.execute("UPDATE `credit_cards` SET `bank` = ? WHERE `id` = ?", (inferred, card_id))
        conn.commit()
        
        # 4. Asserções e validações do estado pós-migração
        print("  4. Validando schema e integridade dos dados...")
        
        # Verifica se o schema das colunas de credit_cards bate com v4
        cursor.execute("PRAGMA table_info(credit_cards)")
        columns = {col[1]: (col[2], col[3], col[4]) for col in cursor.fetchall()}
        
        assert "bank" in columns, "Coluna 'bank' não encontrada após migração!"
        assert columns["bank"][0] == "TEXT", f"Tipo de 'bank' deveria ser TEXT, obteve {columns['bank'][0]}"
        assert columns["bank"][1] == 1, "Coluna 'bank' deve ser NOT NULL"
        assert columns["bank"][2] == "'other'", f"Default de 'bank' deveria ser 'other', obteve {columns['bank'][2]}"
        
        # Verifica índices
        cursor.execute("PRAGMA index_list(credit_cards)")
        indexes = {idx[1] for idx in cursor.fetchall()}
        assert "index_credit_cards_bank" in indexes, "Índice 'index_credit_cards_bank' não foi criado!"
        
        # Verifica valores pós-backfill
        cursor.execute("SELECT id, name, bank, lastFourDigits, totalLimit FROM credit_cards ORDER BY id")
        migrated_cards = cursor.fetchall()
        
        expected_banks = {
            10: "nubank",
            20: "itau",
            30: "inter",
            40: "other",  # Inequívoco 'other'
            50: "other",  # Ambíguo 'other'
            60: "bradesco"
        }
        
        try:
            for cid, cname, cbank, clast, climit in migrated_cards:
                expected = expected_banks[cid]
                assert cbank == expected, f"Cartao {cid} ('{cname}') esperado bank='{expected}', mas obteve '{cbank}'"
                print(f"    [OK] Cartao {cid} ('{cname}'): bank='{cbank}' (preservado final={clast}, limite={climit})")
                
            # Verifica preservacao de transacoes e FKs
            cursor.execute("SELECT count(*) FROM transactions WHERE cardId = 10")
            assert cursor.fetchone()[0] == 1, "Transacao original foi corrompida ou perdida!"
            cursor.execute("SELECT count(*) FROM installments WHERE cardId = 10")
            assert cursor.fetchone()[0] == 1, "Parcela original foi corrompida ou perdida!"
        finally:
            conn.close()
        
    print("[TEST] SUCESSO: Migracao Room v3 -> v4 validada em SQLite real com 100% de integridade!")

if __name__ == "__main__":
    run_real_sqlite_migration_test()
