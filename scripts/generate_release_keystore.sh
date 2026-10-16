#!/usr/bin/env bash
# CardControl - persistent release signing setup
set -euo pipefail

REPOSITORY="kauankelvin7/CardControl"
OUTPUT_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/cardcontrol-signing"
ALIAS="cardcontrol"
SET_GITHUB_SECRETS=0
ACKNOWLEDGE_UPGRADE_COMPATIBILITY=0

while (($#)); do
  case "$1" in
    --output-dir)
      shift
      [[ $# -gt 0 ]] || { echo "Missing value for --output-dir" >&2; exit 2; }
      OUTPUT_DIR="$1"
      ;;
    --alias)
      shift
      [[ $# -gt 0 ]] || { echo "Missing value for --alias" >&2; exit 2; }
      ALIAS="$1"
      ;;
    --set-github-secrets) SET_GITHUB_SECRETS=1 ;;
    --acknowledge-upgrade-compatibility) ACKNOWLEDGE_UPGRADE_COMPATIBILITY=1 ;;
    --help)
      echo "Usage: $0 --acknowledge-upgrade-compatibility [--output-dir DIR] [--alias ALIAS] [--set-github-secrets]"
      echo "Without --set-github-secrets, this script does not change GitHub settings."
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

if ((ACKNOWLEDGE_UPGRADE_COMPATIBILITY == 0)); then
  echo "ERROR: Key generation blocked until existing APK certificates and the upgrade strategy are validated." >&2
  exit 1
fi

command -v keytool >/dev/null 2>&1 || { echo "ERROR: keytool not found." >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl not found." >&2; exit 1; }

if ((SET_GITHUB_SECRETS)); then
  command -v gh >/dev/null 2>&1 || { echo "ERROR: Install GitHub CLI and run gh auth login first." >&2; exit 1; }
  gh auth status >/dev/null 2>&1 || { echo "ERROR: GitHub CLI is not authenticated." >&2; exit 1; }
  gh repo view "$REPOSITORY" >/dev/null 2>&1 || { echo "ERROR: Authenticated account cannot access $REPOSITORY." >&2; exit 1; }
  EXISTING_SECRETS="$(gh secret list --repo "$REPOSITORY" --json name --jq '.[].name')" || { echo "ERROR: Cannot inspect existing secrets; key generation cancelled." >&2; exit 1; }
  EXISTING_VARIABLES="$(gh variable list --repo "$REPOSITORY" --json name --jq '.[].name')" || { echo "ERROR: Cannot inspect existing variables; key generation cancelled." >&2; exit 1; }
  for name in CARDCONTROL_KEYSTORE_BASE64 CARDCONTROL_KEYSTORE_PASSWORD CARDCONTROL_KEY_ALIAS CARDCONTROL_KEY_PASSWORD; do
    grep -Fxq "$name" <<< "$EXISTING_SECRETS" && { echo "ERROR: Signing secret $name already exists; refusing key rotation." >&2; exit 1; }
  done
  for name in CARDCONTROL_CERT_SHA256 CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED; do
    grep -Fxq "$name" <<< "$EXISTING_VARIABLES" && { echo "ERROR: Release variable $name already exists; refusing to replace signing configuration." >&2; exit 1; }
  done
fi

umask 077
mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"
KEYSTORE_PATH="$OUTPUT_DIR/cardcontrol-release.jks"
ENV_PATH="$OUTPUT_DIR/release-secrets.env"
if [[ -e "$KEYSTORE_PATH" || -e "$ENV_PATH" ]]; then
  echo "ERROR: A keystore or credentials file already exists in $OUTPUT_DIR." >&2
  echo "Refusing to overwrite an existing signing identity." >&2
  exit 1
fi

STORE_PASS="$(openssl rand -hex 32)"
KEY_PASS="$STORE_PASS"
export CARDCONTROL_KEYSTORE_PASSWORD="$STORE_PASS"
export CARDCONTROL_KEY_PASSWORD="$KEY_PASS"
trap 'unset CARDCONTROL_KEYSTORE_PASSWORD CARDCONTROL_KEY_PASSWORD' EXIT
echo "Generating a persistent RSA 2048 release key..."
keytool -genkeypair -keystore "$KEYSTORE_PATH" -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass:env CARDCONTROL_KEYSTORE_PASSWORD -keypass:env CARDCONTROL_KEY_PASSWORD \
  -dname "CN=CardControl, OU=Mobile Security, O=CardControl Project, L=Sao Paulo, ST=SP, C=BR" >/dev/null
chmod 600 "$KEYSTORE_PATH"

FINGERPRINT="$(keytool -list -v -keystore "$KEYSTORE_PATH" -alias "$ALIAS" -storepass:env CARDCONTROL_KEYSTORE_PASSWORD | sed -nE 's/.*SHA256: ([[:xdigit:]:]+).*/\1/p' | head -n 1)"
[[ "$FINGERPRINT" =~ ^([[:xdigit:]]{2}:){31}[[:xdigit:]]{2}$ ]] || { echo "ERROR: Could not verify certificate SHA-256 fingerprint." >&2; exit 1; }
KEYSTORE_BASE64="$(base64 < "$KEYSTORE_PATH" | tr -d '\r\n')"
printf '%s\n' \
  '# CardControl release credentials; keep outside Git and store backup securely' \
  "CARDCONTROL_KEYSTORE_BASE64=$KEYSTORE_BASE64" \
  "CARDCONTROL_KEYSTORE_PASSWORD=$STORE_PASS" \
  "CARDCONTROL_KEY_ALIAS=$ALIAS" \
  "CARDCONTROL_KEY_PASSWORD=$KEY_PASS" > "$ENV_PATH"
chmod 600 "$ENV_PATH"
unset CARDCONTROL_KEYSTORE_PASSWORD CARDCONTROL_KEY_PASSWORD
trap - EXIT

echo "Keystore saved locally: $KEYSTORE_PATH"
echo "Protected credentials file saved locally: $ENV_PATH"
echo "Certificate SHA-256: $FINGERPRINT"

if ((SET_GITHUB_SECRETS)); then
  set_secret() {
    printf '%s' "$2" | gh secret set "$1" --repo "$REPOSITORY"
  }
  set_secret CARDCONTROL_KEYSTORE_BASE64 "$KEYSTORE_BASE64"
  set_secret CARDCONTROL_KEYSTORE_PASSWORD "$STORE_PASS"
  set_secret CARDCONTROL_KEY_ALIAS "$ALIAS"
  set_secret CARDCONTROL_KEY_PASSWORD "$KEY_PASS"
  gh variable set CARDCONTROL_CERT_SHA256 --body "$FINGERPRINT" --repo "$REPOSITORY"
  gh variable set CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED --body true --repo "$REPOSITORY"
  CONFIGURED_SECRETS="$(gh secret list --repo "$REPOSITORY" --json name --jq '.[].name')"
  for name in CARDCONTROL_KEYSTORE_BASE64 CARDCONTROL_KEYSTORE_PASSWORD CARDCONTROL_KEY_ALIAS CARDCONTROL_KEY_PASSWORD; do
    grep -Fxq "$name" <<< "$CONFIGURED_SECRETS" || { echo "ERROR: GitHub did not confirm secret $name." >&2; exit 1; }
  done
  CONFIGURED_FINGERPRINT="$(gh variable list --repo "$REPOSITORY" --json name,value --jq '.[] | select(.name == "CARDCONTROL_CERT_SHA256") | .value')"
  [[ "${CONFIGURED_FINGERPRINT//:/}" == "${FINGERPRINT//:/}" ]] || { echo "ERROR: GitHub did not confirm the expected certificate fingerprint." >&2; exit 1; }
  CONFIGURED_COMPATIBILITY="$(gh variable list --repo "$REPOSITORY" --json name,value --jq '.[] | select(.name == "CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED") | .value')"
  [[ "${CONFIGURED_COMPATIBILITY,,}" == "true" ]] || { echo "ERROR: GitHub did not confirm the explicit compatibility decision." >&2; exit 1; }
  echo "GitHub secrets and release variables were confirmed."
else
  echo "GitHub was not changed. Configure all four secrets and both repository variables manually before release."
  echo "Repository settings: https://github.com/kauankelvin7/CardControl/settings/secrets/actions"
  echo "Set CARDCONTROL_RELEASE_COMPATIBILITY_APPROVED=true only for the documented no-installation transition."
fi

echo "No release was published. Keep an offline backup of the keystore and credentials."
