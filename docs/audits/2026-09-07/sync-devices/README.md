# Sincronização em aparelhos físicos — 7 de setembro de 2026

**Bloqueador de publicação reproduzido:** uma mesclagem local ainda não publicada recebeu status SYNCED; após queda de rede, a repetição substituiu a edição pela versão antiga do Drive. O EPUB permaneceu íntegro. **Corrigido no código:** versões mescladas ficam PENDING_UPLOAD e não avançam lastSyncedAt antes do commit. A regressão isolada falhou antes do patch e passou após o patch nos estágios de payload, record e commit; no Drive real uma nova falha de DNS deixou a mesclagem corretamente PENDING_UPLOAD. A retomada final entre aparelhos foi pausada a pedido do usuário.

**Rodada pausada a pedido do usuário em 07/09/2026.** Evidências locais em `.codex-artifacts/sync-qa-2026-09-07/`. O release corrigido foi reinstalado nos dois aparelhos; não considerar concluídos os cenários ainda pendentes abaixo.

## Ambiente e autorização

- Celular SM-S928B, Android 16, conexão ADB por Wi-Fi.
- Tablet SM-X910, mesma conta do Drive usada pelo Foliore.
- Primeiro teste no release 0.1.0 assinado; demais cenários de motor usam variante `qa`, mesma assinatura e applicationId, com comandos de diagnóstico excluídos do release.
- O usuário confirmou que os EPUBs são de teste e autorizou exclusão. Antes da retirada dos arquivos divergentes, foram arquivados banco de dados e arquivos locais, verificando a presença dos 175 originais.
- Nenhuma chave OAuth, senha ou token foi exportado pela instrumentação. O acesso autenticado ao Drive permanece dentro do processo do app, com escopo appDataFolder.

## Achados já reproduzidos

1. **Um original com hash divergente interrompe toda a biblioteca.** O primeiro envio falhou na preparação local com `Local original EPUB checksum mismatch`. Dos 175 livros (442.938.412 bytes registrados), 13 divergiam do hash salvo. Os 13 ZIPs têm CRCs válidos; isso não comprova que conservam o conteúdo original. A causa histórica da divergência não foi determinada. A validação de integridade funcionou, porém o app mostrou somente erro genérico e não identificou os livros afetados. Os 13 foram retirados da rodada após backup; os demais 162 foram mantidos.
2. **Retomada do primeiro lote reenvia dados já transferidos.** Foi executado `am force-stop` após 51 uploads de payload de livros, antes do commit. Nenhum manifesto foi publicado e a biblioteca local permaneceu íntegra. Na tentativa seguinte os mesmos 51 livros foram reenviados. Os diretórios sem commit permanecem remotos para coleta posterior; não há reutilização desses uploads na retomada atual.
3. **Upload faz verificação por download integral.** O perfil CPU de 29,88 s registrou 2.878 amostras sem perda; grande parte do CPU dos workers estava no caminho de download de verificação, leitura/escrita e TLS. O tempo de CPU agregado foi aproximadamente 2,88 s nesse intervalo, portanto não caracteriza saturação de CPU. Perfetto do primeiro minuto também não apontou perda de eventos. O envio completo retomado consumiu 504,21 MiB enviados e 506,69 MiB recebidos pelo UID do app.

## Controles já verificados

- Backup vazio/removido exige decisão explícita antes de recriar a biblioteca.
- Tablet consultado durante envio não publicado: 0 uploads, 0 downloads e 0 conflitos em 2,623 s.
- Após encerramento forçado: 162 registros locais presentes, originais com hash íntegro, manifesto remoto ausente.
- Livro QA importado durante o segundo envio: inclusão local concluída em 199 ms; permaneceu LOCAL_ONLY após o lote anterior e foi enviado na sincronização seguinte.
- 64 testes de unidade Android/shared aprovados.
- Motor real em duas bibliotecas isoladas no tablet: importação, envio, restauração, falha injetada, repetição, preservação do original/preparado, no-op, mesclagem de campos, conflitos simultâneos e exclusão concorrente aprovados. A matriz adicional de publicação e download interrompidos também passou, incluindo rollback de download inválido e recuperação posterior.

## Limites de interpretação

- PSS e contadores de frames incluem a tela visível. A primeira captura Perfetto inclui a abertura da variante QA, portanto frames longos nesse trecho não são atribuídos automaticamente à sincronização.
- Captura Simpleperf em app debuggable tem overhead; o intervalo não é benchmark puro do release.
- Bytes por UID incluem tráfego do processo durante a operação; autenticação pelo Google Play Services e tráfego de outros apps não são contabilizados.
- Falhas de quota e determinadas falhas do provedor serão simuladas; não se pretende esgotar armazenamento nem quota da conta real.

Referência para interpretação das capturas: [Perfetto Trace Processor](https://perfetto.dev/docs/analysis/trace-processor).

## Medições concluídas

| Operação | Livros | Tempo | Enviado pelo app | Recebido pelo app |
|---|---:|---:|---:|---:|
| Envio completo após encerramento forçado | 162 | 9 min 38,8 s | 504,21 MiB | 506,69 MiB |
| Restauração no tablet | 162 | 3 min 04,4 s | 11,20 MiB | 493,51 MiB |
| Sem alterações, repetição 1 | 163 | 22,383 s | 0,028 MiB | 0,574 MiB |
| Sem alterações, repetição 2 | 163 | 22,079 s | 0,024 MiB | 0,573 MiB |
| Sem alterações, repetição 3 | 163 | 19,719 s | 0,024 MiB | 0,574 MiB |

A restauração coincidiu em IDs, hashes dos originais, título, autores, descrição e revisão dos 162 livros. As medições são execuções reais nesta rede, sem extrapolação para outras conexões. O motor roda na variante QA; a medição não equivale a um benchmark exclusivo do release.

## Achados adicionais

4. **Sincronizar sem mudanças ainda custa cerca de 20–22 s.** Não houve envio ou download de livros nessas três execuções. A preparação copia e calcula hashes dos arquivos locais; o acesso aos registros do journal e às estatísticas remotas também mantém custo de rede. É preciso medir e reduzir esses componentes separadamente sem remover a validação de integridade.
5. **Encerramento forçado deixa cache de envio abandonado.** Foram encontrados 486.627 KiB (aproximadamente 475 MiB) em um diretório `cache/sync-upload` da primeira tentativa. O `finally` remove o diretório da execução atual, mas não roda após a morte do processo. O diretório específico abandonado foi removido durante a QA; o código de produção ainda precisa de coleta segura de sessões antigas.
6. **Alteração somente de metadados reenvia o payload do livro.** No EPUB sintético pequeno, a repetição se recuperou de um upload parcial em 54,1 s. Em livros grandes, reaproveitar os originais imutáveis já confirmados deve reduzir tráfego e duração.

## Conflitos reais já verificados

- Edição de título no celular e autor no tablet, antes de sincronizar: os dois campos foram mesclados e propagados.
- Edição simultânea do título nos dois aparelhos: ambos publicaram sem observar a alteração do outro; após nova sincronização, os dois aparelhos tinham as mesmas duas versões e os mesmos hashes dos originais (164 livros, incluindo dois sintéticos QA).
- O tablet apresentou duas falhas reais de resolução de `www.googleapis.com`, com resultado `GoogleDriveUnavailable`. Após reabrir o processo, a sincronização convergiu. Não se determinou a causa do DNS nem se deve concluir que reiniciar seja sempre necessário.
- Upload truncado injetado no Drive real: a repetição automática refez o envio e publicou uma versão válida.
- Marcador de commit truncado injetado no Drive real: a operação falhou; o outro aparelho continuou lendo a versão anterior válida.

## Correção funcional prioritária

**Falha de download é registrada como conflito e pode encerrar o worker com sucesso.** No Drive real, o original baixado foi truncado pela instrumentação. O motor preservou os metadados e bytes locais anteriores, mas retornou `Synced(uploaded=0, downloaded=0, conflicts=1)`. `FolioreSyncWorker` transforma qualquer `Synced` em `Result.success()`. Assim, uma falha de transferência esgotada pode ficar sem nova tentativa automática e aparecer como conflito de edição. Separar falhas transitórias, integridade inválida e conflitos de conteúdo é necessário para a recuperação e para mensagens úteis ao usuário.

## Perfil e limites de interface

- Simpleperf: 29,879 s, 2.878 amostras, nenhuma amostra perdida. Aproximadamente 2,878 s de CPU agregado; o perfil indica trabalho de I/O/rede, sem evidência de saturação de CPU nesse trecho.
- Perfetto, primeiro minuto do envio: thread QA 4,69 s de CPU; três workers aproximadamente 2,84 s somados; thread principal 0,383 s. Primeiro minuto da restauração: workers aproximadamente 9,09 s somados; principal 0,007 s. Sem erros/perda de eventos nas consultas efetuadas.
- A amostra gfxinfo de envio tinha somente 16 frames, incluindo abertura da tela. É insuficiente para atribuir uma taxa representativa de travamentos à sincronização ou validar fluidez durante rolagem.
- PSS após envio completo: aproximadamente 196 MiB; após restauração: aproximadamente 237 MiB. A variação de tela, variante e carga impede concluir ausência ou presença de vazamento de memória a partir desses snapshots.

## Falhas simuladas e cobertura

A injeção de quota (`403 storageQuotaExceeded`) e indisponibilidade (`503`) interrompeu o motor sem publicar alterações. Esses casos não esgotaram quota real e não exercitaram a mensagem da UI nem a política de backoff do WorkManager. Os testes isolados complementam os dispositivos conectados com falha em arquivo, registro e marcador de commit, edição durante envio, escopo por conta e coleta por retenção com relógio controlado. Não equivalem a uma execução real de 30 dias.

Evidências resumidas: [medições](measurements.json), [matriz isolada](isolated-safety.txt), [convergência simultânea](concurrent-conflict-verification.json). Os arquivos de diagnóstico com títulos da biblioteca, banco e EPUBs permanecem na pasta local ignorada pelo Git.

## Limpeza dos testes

A instrumentação removeu os 54 diretórios imutáveis criados pela primeira tentativa interrompida, após reler os marcadores/records publicados, validar seus checksums e confirmar que nenhum deles referenciava os diretórios selecionados. Conteúdo antigo de outro protocolo e histórico válido foram preservados. O diretório de cache local abandonado também foi removido. A retenção de histórico continua sendo de 30 dias; isso não significa ausência imediata de todos os objetos antigos na pasta oculta.

## Falha de mesclagem interrompida — correção

A queda real de rede ocorreu depois de aplicar uma mesclagem local e antes de enviá-la. A descrição sintética “Network interruption recovery” estava na revisão 8, mas marcada SYNCED. Na retomada, o app baixou a revisão 7 e substituiu a descrição. O hash do EPUB não mudou. Isso comprova perda da edição de metadados, sem perda do arquivo original neste caso.

O patch preserva PENDING_UPLOAD e o último lastSyncedAt confirmado para metadados com autoMerged. A marca SYNCED só é aplicada pelo caminho de publicação bem-sucedida. Foram adicionadas regressões com duas bibliotecas isoladas e nova instância do motor a cada tentativa; falhas após upload parcial, record e commit mantêm os dois campos e os bytes originais.

Evidências: [perda reproduzida](unpublished-merge-loss.json), [regressão antes](merge-regression-before.txt), [regressão depois](merge-regression-after.txt).

A desconexão de rede também desligou o ADB por Wi-Fi. O processo shell de religação não concluiu, e o usuário reativou o Wi-Fi; portanto a interrupção durou alguns minutos, não os 25 s inicialmente planejados. O app esgotou cinco tentativas e retornou GoogleDriveUnavailable em 76,2 s. Wi-Fi e dados móveis foram reativados. Esse problema de religação pertence ao mecanismo de QA, não ao Foliore.

## Ponto de retomada

- Celular e tablet: 164 livros cada, sendo 162 da biblioteca e dois livros sintéticos QA. Todos os originais conferidos no ponto de pausa têm SHA-256 igual ao registrado.
- No tablet, o livro sintético principal contém autor mesclado e descrição local “Network interruption recovery — retained after fix”, com estado PENDING_UPLOAD. O envio corrigido encontrou DNS indisponível; a edição permanece local para retomada. No celular, o autor já foi publicado, mas a descrição ainda é a versão anterior.
- Não apagar os livros QA antes de concluir a repetição e verificar a propagação da descrição pendente.
- **Pendentes nos aparelhos reais:** concluir a retomada da mesclagem corrigida; executar WorkManager em segundo plano e verificar deduplicação de agendamento; exclusão concorrente com edição offline; remover os livros sintéticos e conferir os 162 originais finais nos dois aparelhos. Exclusão concorrente e retenção já passaram no motor isolado.
- O teste de queda de rede real foi concluído com erro controlado e revelou a perda de metadados corrigida. A nova tentativa após o patch permanece pendente; não alegar validação completa do fluxo corrigido em rede real.
- Os 13 arquivos com hash divergente retirados no início continuam no backup privado local junto ao banco e aos 175 originais iniciais. A causa da divergência continua desconhecida.
- Melhorias ainda abertas: falhas de download tratadas como conflito/sucesso, erro por livro em vez de bloquear o lote inteiro, redução do custo de no-op e de metadados, retomada de payloads já enviados e coleta de cache abandonado.

Release corrigido: `Foliore-0.1.0-release-sync-fix-2026-09-07.apk`, SHA-256 `832d68ad9e3b5b3d000c921906417c6cd13264594c35f29b218447b6e3e71788`, assinatura v2 válida, mesmo certificado, sem debuggable. O APK fica em Downloads; binários, chaves, banco e EPUBs não são enviados ao Git.

Na pausa: release instalado com sucesso nos dois aparelhos, flags sem DEBUGGABLE, tempos de apagamento da tela restaurados para 120.000 ms e dados móveis do celular reativados. As preferências originais de auto-sync foram preservadas (celular ligado; tablet desligado).
