#!/usr/bin/env bash
# CardControl - Gerador Seguro de Chave de Release v2.2.0
# Gera nova keystore JKS e prepara segredos para GitHub Actions.

set -euo pipefail

OUTPUT_DIR="${1:-./temp_keystore}"
ALIAS="${2:-cardcontrol}"

echo "=========================================================="
echo " CardControl - Gerador Seguro de Chave de Release v2.2.0 "
echo "=========================================================="

KEYTOOL_BIN="$(which keytool || true)"
if [[ -z "$KEYTOOL_BIN" ]]; then
  echo "ERRO: keytool não encontrado no PATH." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
KEYSTORE_PATH="$OUTPUT_DIR/cardcontrol-release.jks"

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "AVISO: $KEYSTORE_PATH já existe. Removendo para gerar nova chave..."
  rm -f "$KEYSTORE_PATH"
fi

STORE_PASS="$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)"
KEY_PASS="$STORE_PASS"

echo "[1/4] Gerando par de chaves RSA 2048 bits com validade de 10.000 dias..."
keytool -genkeypair \
  -v \
  -keystore "$KEYSTORE_PATH" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=CardControl, OU=Mobile Security, O=CardControl Project, L=Sao Paulo, ST=SP, C=BR" >/dev/null

echo "[2/4] Keystore gerada em: $KEYSTORE_PATH"

FINGERPRINT="$(keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$ALIAS" -storepass "$STORE_PASS" | grep "SHA256:" | awk '{print $2}' || true)"
echo ">> SHA-256 Fingerprint: $FINGERPRINT"

KEYSTORE_BASE64="$(base64 -w 0 "$KEYSTORE_PATH" 2>/dev/null || base64 "$KEYSTORE_PATH")"

ENV_PATH="$OUTPUT_DIR/release-secrets.env"
cat <<EOF > "$ENV_PATH"
# CardControl - Segredos de Assinatura de Release (MANTENHA FORA DO GIT)
CARDCONTROL_KEYSTORE_BASE64=$KEYSTORE_BASE64
CARDCONTROL_KEYSTORE_PASSWORD=$STORE_PASS
CARDCONTROL_KEY_ALIAS=$ALIAS
CARDCONTROL_KEY_PASSWORD=$KEY_PASS
EOF
chmod 600 "$ENV_PATH" "$KEYSTORE_PATH"

echo "[3/4] Segredos salvos em: $ENV_PATH"

if command -v gh >/dev/null 2>&1; then
  echo "[4/4] Provisionando segredos no GitHub Actions via gh CLI..."
  echo "$KEYSTORE_BASE64" | gh secret set CARDCONTROL_KEYSTORE_BASE64
  echo "$STORE_PASS" | gh secret set CARDCONTROL_KEYSTORE_PASSWORD
  echo "$ALIAS" | gh secret set CARDCONTROL_KEY_ALIAS
  echo "$KEY_PASS" | gh secret set CARDCONTROL_KEY_PASSWORD
  echo ">> SUCESSO: Segredos configurados no GitHub Actions com êxito!"
else
  echo "[4/4] Cadastre os 4 segredos em: https://github.com/kauankelvin7/CardControl/settings/secrets/actions"
fi

echo "=========================================================="
echo " CONFIGURAÇÃO CONCLUÍDA! Guarde backup offline seguro.   "
echo "=========================================================="
