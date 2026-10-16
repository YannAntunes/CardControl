#!/usr/bin/env python3
"""
Teste automatizado para validar que build_antigravity_bundle.py
bloqueia rigorosamente arquivos-canário sensíveis (chaves, senhas, .env, build, etc.)
"""
import os
import sys
import tempfile
import zipfile
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS_DIR))
import build_antigravity_bundle as bundler

def run_canary_test():
    print("[TEST] Iniciando teste de canários para empacotamento...")

    with tempfile.TemporaryDirectory() as tmpdir:
        root = Path(tmpdir)

        # 1. Arquivos legítimos esperados
        legitimate_files = [
            root / "README.md",
            root / "app" / "src" / "main" / "java" / "MainActivity.kt",
            root / "build.gradle.kts"
        ]
        for f in legitimate_files:
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text("legitimate content", encoding="utf-8")

        # 2. Arquivos-canário que DEVEM ser filtrados
        canary_files = [
            root / "app" / "release.keystore",
            root / "signing.jks",
            root / "app-release.apk",
            root / "app-release.aab",
            root / "local.properties",
            root / "keystore.properties",
            root / "key.properties",
            root / "secrets.properties",
            root / ".env",
            root / ".env.local",
            root / ".env.production",
            root / "debug.log",
            root / "backup.bak",
            root / ".idea" / "workspace.xml",
            root / ".agent" / "TASK.md",
            root / "build" / "output.txt",
            root / ".git" / "config",
            root / ".cxx" / "cmake.o",
            root / ".externalNativeBuild" / "build.ninja",
            root / "dist" / "app-v2.1.0.apk",
            root / "hs_err_pid1234.log",
            root / "replay_pid5678.log",
            root / "temp_keystore" / "cardcontrol-release.jks",
            root / "release-credentials.txt",
            root / "release-secrets.env",
            root / "app" / "cert.p12",
            root / ".DS_Store",
            root / "Thumbs.db"
        ]
        for f in canary_files:
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text("SENSITIVE CANARY", encoding="utf-8")

        # 3. Gerar zip de teste usando a lógica de empacotamento
        zip_path = root / "test_bundle.zip"
        count = 0
        with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
            for dirpath, dirs, files in os.walk(root):
                dirs[:] = [d for d in dirs if d not in bundler.EXCLUDED_DIRS]
                for file in files:
                    full_path = Path(dirpath) / file
                    rel_path = full_path.relative_to(root)
                    if bundler.should_include(rel_path):
                        zf.write(full_path, arcname=rel_path.as_posix())
                        count += 1

        # 4. Validar conteúdo do ZIP gerado
        with zipfile.ZipFile(zip_path, "r") as zf:
            names = set(zf.namelist())

        print(f"[TEST] Total de arquivos incluidos no ZIP de teste: {len(names)}")

        # Verifica arquivos legítimos
        for leg in legitimate_files:
            rel = leg.relative_to(root).as_posix()
            assert rel in names, f"Arquivo legitimo '{rel}' deveria estar no ZIP!"

        # Verifica bloqueio estrito de TODOS os canários
        for canary in canary_files:
            rel = canary.relative_to(root).as_posix()
            assert rel not in names, f"ALERTA DE SEGURANCA: Arquivo canario sensivel '{rel}' vazou no ZIP!"

        print(f"[TEST] SUCESSO: Todos os {len(canary_files)} arquivos-canario sensiveis foram bloqueados!")

if __name__ == "__main__":
    run_canary_test()
