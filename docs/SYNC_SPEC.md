# Sincronização — primeira versão pública

O app ainda não foi lançado. O protocolo atual usa a identificação interna `v4`, mas não mantém leitura ou migração de backups de versões públicas anteriores. A migração SQLite existente serve apenas para preservar a biblioteca de desenvolvimento instalada.

## Local e remoto

A biblioteca local funciona offline. A sincronização pode usar uma pasta escolhida pelo Storage Access Framework ou exclusivamente o `appDataFolder` oculto do Google Drive, com escopo `drive.appdata`. Nada é transferido para uma pasta visível do Drive.

Cada revisão remota tem um diretório imutável `foliore-v4-data-<UUID>`, com `original.epub`, `prepared.epub` opcional e `book-state.zip`. O EPUB original mantém seus bytes e SHA-256; o preparado é uma cópia separada. Nomes não dependem de autor ou título.

O índice é um diário de registros imutáveis: `foliore-v4-record-<UUID>.json` e um marcador `foliore-v4-commit-<UUID>.json` contendo seu SHA-256. A publicação ocorre depois de enviar e verificar os arquivos, gravar e reler o registro e publicar o marcador. Um marcador interrompido não publica dados parciais; um registro publicado corrompido interrompe a leitura com erro.

## Ordem e conflitos

Cada registro identifica todos os commits conhecidos pelo dispositivo. Uma alteração que conhece a anterior a sucede, mesmo que o relógio do aparelho esteja atrasado. Horários servem para apresentação; não decidem qual edição concorrente deve ser destruída.

Campos de metadados diferentes são mesclados quando existe a mesma base e as versões do arquivo original e da capa são compatíveis. Alterações incompatíveis preservam uma cópia identificada como conflito recuperado, editável na biblioteca. IDs diferentes continuam distintos mesmo com o mesmo hash do EPUB. O resultado converge entre dispositivos após sincronização.

O processo coordena sincronização manual, tarefas de fundo e exclusão. Edições locais têm bloqueio separado para criar snapshots e publicar transações; alterações feitas durante transferência não podem ser marcadas como sincronizadas por engano.

## Exclusão e coleta

Ausência local, erro de download ou diretório desconhecido **não** autorizam exclusão remota. A intenção de excluir é persistida antes de remover o registro local e vinculada à conta/pasta e à versão vista. Uma edição remota diferente é preservada. Uma exclusão recebida enquanto há edição local offline preserva essa edição como outra cópia.

Apagar o backup inteiro cria uma nova época de exclusão. Escritores que começaram antes dela não podem repovoar o índice ativo. Recriação explícita usa a nova época.

Revisões substituídas, objetos abandonados e payloads de livros excluídos ficam retidos por pelo menos 30 dias antes da coleta. A coleta só considera nomes pertencentes ao protocolo e referências dos registros retidos, e limita cada lote. No Drive, usa a data retornada pelo servidor; sem esse dado, adia a coleta. No SAF depende do relógio do dispositivo/provedor. Arquivos desconhecidos são preservados, não apagados por heurística. A limpeza acontece durante uma sincronização bem-sucedida, não em um prazo exato de calendário.

As cópias de conflitos aparecem na biblioteca. A retenção de revisões antigas não tem ainda uma tela de histórico/restauração; não deve ser anunciada como uma lixeira disponível ao usuário. Arquivos de versões locais anteriores também são preservados, sem coleta local automática nesta implementação.

## Verificação e limites

`SyncSafetyTest` cobre causalidade, concorrência, mesclagem, exclusão, publicação interrompida, época de exclusão, coleta e limites de entrada. `SyncSafetyHarness` (somente debug) executa o motor real em duas bibliotecas Android isoladas e injeta falhas, sem tocar na biblioteca do usuário ou no Drive real.

Ainda são necessários testes autenticados com dois aparelhos, interrupção do processo/rede durante transferências, quotas/erros reais do Drive e desempenho com bibliotecas grandes. O motor copia snapshots locais e verifica uploads por download; isso prioriza integridade e tem custo de disco e tráfego. Não há promessa de perda de dados impossível em qualquer falha: o usuário ainda deve conservar seus originais.
