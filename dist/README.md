# Distribuições Oficiais do CardControl (dist/)

Este diretório contém os binários de release compilados, assinados e prontos para distribuição direta (sideload) em aparelhos Android.

---

## 📦 Versão Atual: v2.1.0 (versionCode 3)

| Arquivo | Tamanho | SHA-256 Checksum |
| :--- | :--- | :--- |
| `CardControl-v2.1.0-release.apk` | ~11.3 MB | `B2CB30F1FEDA58BAFA50EFDB655182F6075A68B9767E790A82BB508FD80F90E6` |

---

## 🔒 Certificado de Assinatura Oficial

O aplicativo é assinado com certificado RSA de 2048-bit proprietário e permanente do projeto:
- **Distinguished Name (DN):** `CN=CardControl, OU=Security, O=CardControl Offline, L=Sao Paulo, ST=SP, C=BR`
- **Esquema de Assinatura:** APK Signature Scheme v2 (suportado a partir do Android 7.0+)
- **Fingerprint SHA-256:** `E9:F3:80:BC:6D:F7:6C:12:96:97:88:6B:AA:8A:E2:23:FE:30:9C:FE:3D:FA:0E:B7:7A:2D:9B:85:6A:5E:A9:7D`

### Como verificar a integridade do APK
```bash
apksigner verify --verbose --print-certs dist/CardControl-v2.1.0-release.apk
```

---

## 📲 Como Instalar no Android

1. Baixe o arquivo `CardControl-v2.1.0-release.apk`.
2. Abra o arquivo no gerenciador de downloads do celular e selecione **Instalar**.
3. Se o Android exibir o alerta de segurança do Play Protect, selecione **Mais detalhes** → **Instalar assim mesmo**.
4. Para ativar a leitura automática de notificações bancárias, consulte o guia em [`docs/guides/CONFIGURACAO_E_PERMISSOES.md`](../docs/guides/CONFIGURACAO_E_PERMISSOES.md).
