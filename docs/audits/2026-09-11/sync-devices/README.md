# Retomada dos testes de sincronização — 11 de setembro de 2026

Rodada continuando [o relatório de 7 de setembro](../../2026-09-07/sync-devices/README.md). O usuário manteve a autorização para usar e apagar os livros de teste. A biblioteca mantém seu layout. Nas configurações, o usuário pediu para mover Privacidade, Suporte e a nota sobre originais para o rodapé.

## Recuperação confirmada

A edição que permaneceu PENDING_UPLOAD no tablet desde 7 de setembro foi mesclada, publicada e recebida pelo celular. Os 164 livros, incluindo os dois sintéticos QA, coincidiram em IDs, título, autores, descrição, revisão e hash dos originais. A descrição pendente foi preservada. Evidência: [verificação da retomada](resumed-merge-verification.json).

A retomada levou 89,500 s no tablet (1 download e 1 upload) e 75,223 s no celular (1 download). Essas execuções ainda tinham a rotina anterior de coleta do histórico; não constituem benchmark de sincronização sem mudanças.

## Correções desta rodada

- Falha de download deixou de virar conflito de edição e resultado Synced. Depois de processar as tentativas de download, o motor propaga a falha antes de publicar dados ou avançar o cache da versão base. Downloads concluídos permanecem locais, e os arquivos/metadados do livro que falhou ficam intactos. O serviço preserva a classificação de autenticação, quota, rede ou erro geral; WorkManager repete erros transitórios/gerais, conforme sua política existente.
- A exclusão recebida do Drive remove também as pastas locais de versões. Uma passagem posterior pelos mesmos tombstones conclui a limpeza se o processo foi encerrado depois de remover o registro. O teste isolado confirma que a cópia recuperada mantém o original íntegro.
- A próxima sincronização remove os diretórios de snapshot abandonados por encerramento do processo. Sessões ativas no mesmo processo são protegidas, inclusive execuções sobrepostas; arquivos com outros nomes e links simbólicos ficam fora da coleta. O app atualmente usa um único processo Android.
- A leitura dos registros do journal usa no máximo três requisições concorrentes, respeitando o limite do provedor. Cada marcador e registro continua validado por checksum; a ordem de entrada é preservada e qualquer registro publicado inválido interrompe a leitura. Testes verificam o limite, a equivalência do resultado causal e a rejeição de corrupção.
- A coleta do histórico remoto consulta os registros completos somente se houver objetos do protocolo que já passaram dos 30 dias de retenção. Quando todos são recentes, basta a listagem. A coleta real de objetos antigos mantém a validação de referências e checksums.

## Verificação local

79 testes Android/shared passaram, sem falhas nem testes ignorados. Assemble QA, assemble release e lint release também passaram. Foram acrescentados casos para snapshots abandonados, sessões sobrepostas, limpeza após exceção e adiamento de leituras do journal até o prazo de retenção. A matriz isolada no tablet passou com o novo comportamento de falha de download e com as regressões de mesclagem interrompida.

## Estado final

Propagação em segundo plano, falha de download/recuperação, exclusão concorrente e remoção dos livros QA concluídas. Os 162 livros finais coincidem com o início da rodada em IDs, metadados, revisões e hashes dos originais. A conferência física das pastas removidas e as medições finais também foram concluídas. A versão release final foi instalada por atualização nos dois aparelhos. O hash do APK instalado coincide com o artefato distribuído, e DEBUGGABLE está desativado: [verificação da instalação](release-install-verification.json).

## Segundo plano

A edição foi enviada pelo WorkManager com a tela inicial do celular visível. Dois agendamentos imediatos mantiveram um único ID de trabalho; o estado terminou em SUCCEEDED na primeira execução observada. O tablet recebeu a descrição esperada em seguida. Evidência: [verificação do trabalho](background-verification.json). Esse caso não simula Doze profundo nem as restrições de todos os fabricantes.

## Download inválido no Drive real

Um download do original foi truncado pela instrumentação. O motor retornou erro de tamanho, preservou descrição, hash do arquivo e estado anterior do livro, e a tentativa seguinte recebeu a descrição mais recente com 1 download, 0 uploads e 0 conflitos. Evidência: [download e recuperação](download-failure-verification.json). A corrupção foi injetada depois da transferência, sem alterar o original publicado no Drive. Essa execução exercita o motor; a classificação do serviço e o retry do worker seguem os caminhos existentes do código.

## Limites e trabalho restante

A rodada anterior cobriu quota/503 simulados, encerramento do processo, publicação parcial, conflitos simultâneos e verificação de originais. A rodada atual não repete o desligamento de Wi-Fi que havia interrompido o acesso ADB. Não houve esgotamento de quota real, teste de 30 dias corridos, falha física do armazenamento nem teste em todos os fabricantes. O histórico válido continua retido por 30 dias na pasta oculta.

Continuam abertas as melhorias para identificar os livros com original divergente sem bloquear toda a biblioteca, reaproveitar uploads interrompidos antes do commit e reduzir as cópias locais em uma sincronização sem alterações. O reenvio desnecessário de EPUBs em edições de metadados e a releitura integral do histórico foram tratados nesta rodada. A conclusão dos casos desta rodada não significa que a publicação na Play Store já esteja liberada.

## Exclusão concorrente

Com a mesma versão nos dois aparelhos, o tablet editou a descrição sem sincronizar, e o celular excluiu o livro. A exclusão foi publicada primeiro. O tablet retirou o ID original e preservou sua edição em uma cópia recuperada; essa cópia foi enviada e recebida pelo celular. Os dois aparelhos convergiram para os mesmos IDs de recuperação. Evidência: [exclusão e recuperação](deletion-conflict-verification.json).

A verificação de arquivos detectou 183 KiB de versões antigas no diretório do livro excluído remotamente, mesmo sem registro no banco. A correção de limpeza local descrita acima foi adicionada com regressão para uma limpeza interrompida. O conteúdo do livro recuperado é verificado por hash antes e depois dessa exclusão.

A verificação final da biblioteca está em [final-verification.json](final-verification.json). Os backups privados dos 175 originais iniciais continuam no PC, incluindo os 13 arquivos que já apresentavam divergência de hash antes da primeira rodada.

As pastas locais de todos os IDs sintéticos foram verificadas como ausentes nos dois aparelhos após a aplicação do patch, incluindo o diretório antigo de 183 KiB: [verificação das pastas](local-cleanup-verification.json).

## Aceleração da sincronização

No celular SM-S928B, com os mesmos 162 livros e sem alterações:

| Implementação | Tempo total observado |
|---|---:|
| Antes da otimização desta rodada | 66,056 s |
| Leitura do journal em até 3 chamadas paralelas | 30,952 / 28,166 / 28,092 s |
| Cache verificado por versão e checksum, após primeira leitura | 8,599 / 8,615 / 8,660 s |
| Cache + reutilização do snapshot remoto + inspeção completa apenas quando necessária | 6,584 / 5,196 / 5,045 s |

A mediana final foi 5,196 s: cerca de 92% menos tempo / 12,7 vezes mais rápido em relação à execução anterior de 66,056 s. No tablet SM-X910, a primeira leitura do histórico sem cache levou 16,777 s e a repetição com cache, 5,887 s. Esses tempos são medições nessa rede e nessa biblioteca, não garantias em qualquer aparelho ou tamanho de histórico. O tráfego recebido no celular caiu de 526.431 bytes na referência para 40.272–54.370 bytes nas três medições finais. Medição por UID inclui tráfego de autenticação e eventualmente de outros trabalhos do próprio app.

O cache vive no armazenamento privado do aplicativo, separado por conta. Cada sincronização continua consultando o Drive: somente uma entrada com o mesmo ID, versão, tamanho e SHA-256 pode reutilizar bytes locais, conferidos novamente por hash. Entradas removidas ou de versões antigas são descartadas; ausência de versão/checksum, cache corrompido ou cache vazio exige leitura remota. Marcadores de exclusão não usam esse cache. A versão do Drive reflete alterações no servidor conforme a [documentação de arquivos](https://developers.google.com/workspace/drive/api/reference/rest/v3/files).

A inspeção e o motor usam o mesmo snapshot validado e os mesmos pais causais. Uma alteração publicada por outro aparelho depois dessa leitura continua concorrente; não é silenciosamente sobrescrita. A época de exclusão continua sendo verificada novamente antes de publicar. A listagem completa para contar todos os arquivos permanece nas estatísticas do backup e nos casos de recuperação, sem percorrer todos os payloads em uma sincronização normal com índice não vazio.

Uma alteração somente de metadados reaproveita referências a EPUBs imutáveis já publicados quando tamanho e SHA-256 coincidem. Os novos metadados ficam em uma nova pasta de versão; a coleta considera referências em todas as pastas. No recebimento, o original/preparado local pode ser copiado para staging se seus hashes coincidirem com o manifesto, com nova verificação da cópia. Um EPUB preparado novo continua sendo transferido. A matriz isolada verifica os caminhos enviados/recebidos e as falhas de publicação, download e edição durante upload.

Uploads verificam o tamanho e o SHA-256 retornados pelo servidor; se o Drive não fornecer checksum, permanece o download integral para conferência. A disponibilidade do campo é [documentada pelo Google](https://developers.google.com/workspace/drive/api/reference/rest/v3/files). Divergência impede a publicação do índice.

## Exclusão e verificação de uploads no Drive real

A remoção de uma árvore com nove objetos passou de 12 requisições / 7,671 s na implementação recursiva para uma requisição / 0,725 s ao apagar a pasta raiz. A primeira listagem ainda retornava oito descendentes, mas a leitura direta de um deles já retornava HTTP 404. A listagem convergiu para zero após mais 3,380 s de sondagem, além da primeira consulta. Portanto, o tempo da chamada DELETE não representa sozinho o tempo até a atualização de todas as listagens. Um arquivo de controle fora da pasta permaneceu intacto. Houve uma resposta HTTP 500 em uma execução anterior; os dois objetos de controle deixados por essa falha foram identificados e removidos. O teste de convergência passou e sua estrutura foi limpa. [Medições](phone-drive-speed-fixture-observe.json).

A limpeza de histórico e a ação **Apagar do Drive** nas configurações agora agrupam até 100 exclusões por requisição HTTP. A remoção completa continua selecionando raízes e conferindo a listagem final, preservando o marcador de exclusão que impede a recriação automática pelos outros aparelhos. O leitor de respostas valida todos os identificadores/status, aceita 404 para itens já apagados e repete falhas temporárias; um HTTP 200 no envelope não basta para considerar o lote concluído. A coleta só apaga registros depois dos respectivos marcadores, e payloads depois da verificação das referências retidas.

No Drive real, um lote com oito alvos — um deles já apagado — concluiu em 1,779 s, com uma chamada HTTP. Os sete restantes desapareceram e o arquivo de controle permaneceu intacto. [Teste do lote](phone-drive-batch-verified.json). Não foi apagada toda a biblioteca de 162 livros para extrapolar esse tempo: o benchmark cobre o mecanismo usado pela exclusão completa, não um tempo total garantido para bibliotecas maiores. O [Google documenta o limite de 100 operações, respostas individuais e contagem de quota por operação](https://developers.google.com/workspace/drive/api/guides/performance#batch-requests).

Os uploads de 1 MiB (multipart) e 6 MiB (resumable) retornaram SHA-256 válido. A conferência pelo servidor levou cerca de 1 ms sem transferência adicional; a conferência por download demorou, respectivamente, 1,291 s e 1,703 s, além de baixar os arquivos inteiros novamente. Não foi removida a conferência de integridade; foi substituída a transferência redundante quando o servidor fornece o hash.

## Segurança após a aceleração

O ciclo final nos dois aparelhos confirmou edição de descrição sem retransmitir o original, commit interrompido invisível ao outro aparelho, rejeição de bundle de metadados truncado e recuperação na tentativa seguinte. A biblioteca voltou a 162 livros e coincide com a referência em IDs, título, autores, descrição, revisão e SHA-256 de cada original. [Verificação final acelerada](fast-live-verification.json). A pasta do último livro sintético também foi conferida como ausente nos dois aparelhos: [limpeza local](fast-local-cleanup-verification.json).

No primeiro teste de download dessa rodada, a instrumentação tentava truncar apenas `original.epub`. Como esse arquivo passou a ser reaproveitado localmente, nenhuma falha era injetada; esse resultado não foi contado como teste de falha aprovado. O teste foi atualizado para corromper a primeira transferência efetiva, o bundle de metadados, e então confirmou erro e preservação do estado anterior.

Uma edição pequena levou 19,346 s para publicar e 9,731 s para chegar ao tablet. Uma exclusão de livro levou 25,987 s para publicar e 7,393 s para chegar ao tablet nessa rodada. Esses caminhos ainda fazem as etapas de publicação/validação do índice e podem ser otimizados; o resultado de 5–6 s se refere à sincronização sem alterações.

## Clareza nas configurações

Textos de 28 entradas foram condensados nos oito idiomas, preservando o layout da biblioteca. Após solicitação adicional do usuário, a seção Biblioteca foi substituída por uma nota menor no rodapé, junto aos atalhos Privacidade e Suporte. Os atalhos usam alvos de toque acessíveis e podem quebrar linha em telas estreitas. O conjunto inicial em português caiu de 1.663 para 1.125 caracteres antes do ajuste adicional de Suporte. O resumo mostra livros e espaço ocupado; a data usa formato local em vez de ISO técnico. Os textos distinguem desconectar sem apagar, apagar livros/histórico apenas do Drive e preservar arquivos recuperáveis ao recriar o índice. As instruções de Wi-Fi deixam claro que a opção se aplica à sincronização automática.

O rodapé passa a mostrar apenas **Suporte**, com ajuda, e-mail e formulário dentro da janela. O recurso de relatar conteúdo inadequado permanece no app, com linguagem comum. O Google exige [relato dentro do app para conteúdo gerado por IA](https://support.google.com/googleplay/android-developer/answer/13985936?hl=pt-BR) e [e-mail na ficha da loja](https://support.google.com/googleplay/android-developer/answer/9859152?hl=pt-BR), sem impor aquele rótulo longo na página de configurações. O domínio e o serviço de recebimento ainda precisam ser ativados antes do lançamento; a consulta ao site não conseguiu conexão nesta rodada. Nenhuma mensagem foi enviada ao suporte durante estes testes.

O rodapé final foi conferido na versão release instalada no celular e no tablet. A nota sobre os originais usa texto menor; Privacidade e Suporte abrem as respectivas janelas. O e-mail fica dentro de Suporte. Não houve envio de mensagem nem confirmação de exclusão total. Capturas: [celular](settings-phone.png), [tablet](settings-tablet.png), [janela de suporte](support-phone.png). Assemble release e lint release passaram após esse último ajuste.

A sincronização manual na release final terminou com zero envios, recebimentos ou conflitos nos dois aparelhos; as configurações mantiveram os 162 livros no Drive. As preferências de sincronização automática foram preservadas e o tempo de desligamento da tela voltou a dois minutos.
