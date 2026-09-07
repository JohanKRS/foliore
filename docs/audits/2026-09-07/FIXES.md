# Correções e verificação — 7 de setembro de 2026

As alterações estão no workspace, ainda sem commit ou publicação. Por orientação do usuário, qualquer nova mudança de layout precisa de aprovação prévia; a redução experimental da capa foi revertida. O app não foi lançado: a sincronização adota diretamente o novo protocolo, sem compatibilidade com backups de versões públicas anteriores. O relatório original e o scan de segurança foram conservados como evidência da base; esta página descreve o estado posterior às correções.

## Resultado por achado

| Item | Estado | Correção e evidência |
|---|---|---|
| Limpeza remota destrutiva, colisões por autor/título e seleção de raiz | `fixed` | Removida exclusão por ausência local; payloads imutáveis identificados por UUID; raízes ambíguas falham preservando as alternativas. Upload com falha mantém todos os bytes do backup anterior no teste Android. |
| Concorrência e ordenação da sincronização | `fixed` | Diário causal, publicação por registro + checksum, snapshots locais, verificação de bytes e coordenação no processo. Dois dispositivos lógicos no Android convergem após publicações simultâneas; relógio não determina descarte. |
| Exclusões e alterações offline | `fixed` | Intenção durável por conta/pasta e versão; campos distintos mesclados; conflito no mesmo campo e edição offline durante exclusão preservados como cópias editáveis. Testes confirmam as cópias em ambos os dispositivos. |
| Arquivos soltos no Drive | `fixed` para objetos do protocolo | Coleta após retenção de 30 dias, baseada em referências remotas, com data do servidor no Drive e lotes limitados. Teste mantém payload ativo e remove órfão vencido. Tudo continua em `appDataFolder`. Arquivos desconhecidos não são apagados por heurística. |
| Travessia por `bookId` remoto | `fixed` | IDs e caminhos externos validados antes da importação; contenção canônica e staging aleatório. Testes negativos de `..`, separadores, absoluto e caminho vazio preservam sentinela externa; IDs válidos continuam aceitos. |
| SMTP sem validar hostname | `fixed` | Verificação de identidade do endpoint nos dois caminhos TLS antes de autenticar. Teste com certificado confiável aceita `localhost` e recusa `127.0.0.1`, que não está no certificado. O teste cobre o helper compartilhado, não uma entrega real. |
| SMTP plaintext em porta personalizada | `fixed` | TLS obrigatório em todas as portas e guarda antes de AUTH. Servidor local sem STARTTLS recebe recusa, sem AUTH/MAIL/DATA. |
| EPUB/imagem sem limites | `fixed` nos caminhos identificados | Limites de 256 MB de entrada, 32 MB por entrada ZIP, 8 MB por XML/texto, 512 MB de expansão, 10.000 entradas e 80 milhões de pixels com redução antes de alocação. Cópia de capa compartilhada sai da thread principal. ZIP comprimido acima do limite é recusado; ZIP comum funciona. No Android, PNG/JPEG válidos funcionam e PNG com dimensões de 100 milhões de pixels é recusado. Não equivale a fuzzing exaustivo. |
| Exportar/Kindle ignorando rascunho | `fixed` | A ação salva e prepara o rascunho atual antes de exportar/enviar. Exportação real pelo editor gerou o título `Death's End_QA` no OPF, igual ao campo exibido. O título foi restaurado após o teste; nada foi compartilhado externamente. |
| Perda de rascunho ao sair | `fixed` | Voltar do Android, topo, Biblioteca e Configurações passam por Salvar/Descartar/Cancelar. O teste visual de Configurações mostrou o diálogo e Cancelar conservou o texto. |
| Substituições com `$` e `\` | `fixed` | Replacement literal seguro. Preparação de EPUB com esses caracteres executada no Android sem falha. |
| Capa JPEG mantendo caminho/MIME PNG/SVG | `fixed` | Novo caminho JPEG, MIME e referências relativas atualizados em documentos e CSS. Testes de PNG/SVG/JPEG e caminhos relativos/externos. Ainda falta EPUBCheck e leitores externos. |
| Árvore de acessibilidade escondida | `fixed` em código | Semântica permanece disponível sem depender de exploração por toque; navegação anuncia seleção. Serviço TalkBack/Switch Access real ainda precisa de validação. |
| Vidro, rótulos, cabeçalho e densidade | `fixed` localmente | Blur uniforme de 28 dp e camada de 80%, preservando cores desfocadas; rótulo ativo dourado/negrito, secundário contrastante; cabeçalho usa o mesmo princípio. Capa do editor mantém a largura anterior do conteúdo, conforme preferência do usuário. Matriz visual original e capturas pós-correção acompanham o relatório. |
| Backup Android/restauração de chaves | `fixed` | Backup automático desabilitado; indicador de chave configurada depende de descriptografia bem-sucedida. A biblioteca depende do backup explícito do app. |
| Idiomas no AAB | `fixed` em configuração | Splits por idioma desabilitados para acompanhar o seletor interno. O aviso `AppBundleLocaleChanges` desapareceu. Falta instalação via bundle assinado. |
| Transparência no uso de IA | `fixed` em código | Aviso antes de gerar capa descreve envio e custos da chave própria; caminho local removido do prompt. Nenhuma chamada paga foi feita. |
| Política e denúncia | `blocked` para lançamento | Política dentro do app, HTML público e formulário/servidor de denúncia implementados. Publicação do domínio, identidade, caixa de suporte, operação da fila e verificação de produção ainda dependem de configuração externa. |
| Assinatura e distribuição | `blocked` | `bundleRelease` exige assinatura e confirmações de publicação; não produz silenciosamente um bundle de distribuição incompleto. Play Console/OAuth/certificado de distribuição ainda precisam ser configurados. |
| Performance e matriz ampliada | `no_change` na certificação | Ainda requer aparelho físico intermediário, biblioteca grande, Android antigo, 16 KB, tablet, fonte ampliada e serviços assistivos. Minificação não foi ativada sem evidência. |

## Verificação executada

- **48 testes JVM passaram:** 40 Android e 8 compartilhados. `:androidApp:testDebugUnitTest :shared:testAndroidHostTest`.
- **APK debug compilado e instalado**, sem reset da biblioteca de 15 EPUBs usada na revisão. O título de QA foi restaurado para `Death's End` e confirmado no banco local.
- **Lint release:** zero erros, 121 avisos e 4 sugestões. Avisos incluem convenções Compose, traduções/plurais, `commit()` intencional para exclusão durável e recomendações de atualização; isso não é uma declaração de zero dívida técnica.
- **Harness Android isolado:** importação, upload, restauração, falha injetada, retry, original idêntico por SHA-256, preparado separado, sync sem upload redundante, mescla de campos, conflitos, publicações simultâneas, edição offline durante exclusão e escopo por conta. Não usa o Drive real nem altera a biblioteca do usuário.
- **Servidor de denúncias:** teste HTTP local com entrada válida, vazia, longa, JSON inválido/null, persistência antes de recibo, diretório privado e Content-Type. Nenhuma denúncia real enviada.
- **Bloqueio de release:** `bundleRelease` recusou a ausência das confirmações de publicação, com cache de configuração funcionando. Nenhuma chave fictícia ou confirmação falsa foi criada.
- Uma investigação independente de segurança e uma revisão independente do candidato foram realizadas. Quatro regressões identificadas nessa revisão foram corrigidas: amostragem de bitmap, cópias de conflitos materializadas, marcador parcial de commit e precedência dos metadados inline. Testes de regressão foram adicionados.

Logs: `verification/`. Capturas: `screenshots/fixes-*`. Especificação atual: [SYNC_SPEC.md](/home/johan/Projetos/foliore/docs/SYNC_SPEC.md). Operação do canal: [support/README.md](/home/johan/Projetos/foliore/support/README.md).

## Limites e próximos passos externos

Ainda não considero o app pronto para produção sem validar Drive autenticado em dois aparelhos, interrupções reais de rede/processo, SMTP/Kindle, EPUBCheck/leitores, acessibilidade e instalação do AAB assinado em 16 KB. O teste isolado demonstra os cenários listados; não substitui falhas reais dos provedores.

A retenção de revisões antigas não possui tela de histórico/restauração. Cópias de conflitos aparecem normalmente na biblioteca, mas não anunciar uma lixeira de 30 dias ao usuário. Versões locais anteriores são preservadas sem coleta automática; isso e a cópia de snapshots de todos os livros podem consumir espaço/tempo em bibliotecas grandes e devem ser medidos antes de ampliar a escala.

Para `foliore.johankrs.com`: revisar o texto público e a identidade do desenvolvedor, ativar `foliore@johankrs.com`, hospedar `/privacy/` e `/api/content-reports`, atribuir responsável pela fila e confirmar funcionamento em produção. Preencher Data Safety conforme os provedores efetivamente habilitados, configurar assinatura/OAuth e cumprir o teste exigido para a conta Play. Nenhum serviço foi publicado nesta tarefa.

## Ajuste de alinhamento autorizado posteriormente

A pedido do usuário, metadados, descrição e informações de sincronização agora herdam o espaçamento horizontal da própria coluna. No celular, compartilham as bordas da capa (42–1038 px no teste); no tablet, acompanham a coluna direita e seu cabeçalho (926–1878 px). Removidos os recuos próprios das seções e do divisor. A capa mantém seu tamanho. APK compilado e instalado; verificação visual e de limites na árvore Android em celular e tablet, com a resolução do emulador restaurada após o teste. Evidências: `screenshots/align-*` e `verification/alignment.json`.


## Busca de metadados

Corrigidos tratamento de falhas, consultas alternativas, escolha e classificação de edições, obtenção de capas originais e separação dos downloads opcionais. Validação: 56 testes Android aprovados, build e lint concluídos, três livros consultados em fontes reais e fluxo de busca verificado no emulador. Nenhum resultado foi aplicado aos livros de QA. [Detalhes e limitações](metadata-lookup/README.md).
