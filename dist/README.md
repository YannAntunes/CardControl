# Aviso de segurança: distribuição v2.1.0 retirada

**Não instale nem redistribua o APK v2.1.0 deste repositório ou da página de Releases.**

Em 15/10/2026, foi identificado que a chave privada de assinatura e as senhas de release estavam versionadas publicamente. O APK v2.1.0 foi assinado com esse material e deve ser tratado como potencialmente comprometido.

O arquivo APK e o checksum correspondente foram removidos da árvore atual do Git. A assinatura de novos releases permanece desativada enquanto a equipe define uma estratégia segura para a rotação da chave e a atualização das instalações existentes.

A remoção do arquivo deste repositório não retira cópias de releases já publicados, clones, forks, caches ou commits antigos. A release v2.1.0 ainda precisa ser retirada manualmente da página Releases do GitHub.

## Próximos passos antes de voltar a distribuir

1. Tratar a chave e as senhas antigas como comprometidas e não reutilizá-las.
2. Avaliar como os usuários existentes receberão atualizações, considerando a compatibilidade da assinatura e a preservação dos dados locais.
3. Gerar uma nova chave em ambiente privado, configurar os segredos fora do Git e validar a assinatura em dispositivo.
4. Publicar um novo APK somente após CI, análise estática, verificação da assinatura e teste do caminho de atualização.
