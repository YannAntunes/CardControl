#!/usr/bin/env python3
import os
import zipfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ZIP_NAME = "CardControl_v2_2.zip"

EXCLUDED_DIRS = {
    ".git", ".gradle", "build", ".idea", ".agent", "captures",
    "dist", ".system_generated", ".cxx", ".externalNativeBuild", "temp_keystore"
}

EXCLUDED_EXTS = {
    ".keystore", ".jks", ".p12", ".apk", ".aab", ".hprof", ".pyc", ".log", ".bak", ".swp"
}

EXCLUDED_FILES = {
    "local.properties", "keystore.properties", "key.properties", "secrets.properties",
    "release-credentials.txt", "release-secrets.env", ".DS_Store", "Thumbs.db"
}

def should_include(file_path: Path) -> bool:
    for part in file_path.parts:
        if part in EXCLUDED_DIRS:
            return False
    name = file_path.name
    if name == ZIP_NAME or name in EXCLUDED_FILES:
        return False
    if name.startswith(".env"):
        return False
    if file_path.suffix.lower() in EXCLUDED_EXTS:
        return False
    if name.startswith("hs_err_pid") or name.startswith("replay_pid"):
        return False
    return True

def main():
    out_path = PROJECT_ROOT / ZIP_NAME
    count = 0
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(PROJECT_ROOT):
            dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]
            for file in files:
                full_path = Path(root) / file
                rel_path = full_path.relative_to(PROJECT_ROOT)
                if should_include(rel_path):
                    # Usar forward slash para portabilidade do ZIP
                    arcname = rel_path.as_posix()
                    zf.write(full_path, arcname=arcname)
                    count += 1

    print(f"[OK] Bundle '{ZIP_NAME}' gerado com sucesso contendo {count} arquivos do repositorio.")

if __name__ == "__main__":
    main()
