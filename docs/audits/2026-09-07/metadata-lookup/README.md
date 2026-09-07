# Busca de metadados — 7 de setembro de 2026

Ajuste de confiabilidade e relevância, sem alteração do layout. Consultas reais de QA foram somente de leitura: nenhum resultado foi aplicado aos livros e nenhum EPUB foi enviado aos catálogos.

## Causas corrigidas

- As falhas dos provedores eram convertidas em listas vazias. O app podia dizer que não encontrou o livro quando a fonte havia falhado.
- As fontes eram consultadas em sequência e os prazos incluíam downloads opcionais. Uma capa ou descrição lenta podia descartar os dados bibliográficos já encontrados.
- Google Books usava `projection=lite` e apenas quatro resultados. Agora solicita os campos completos e até 12 itens por consulta.
- Open Library combinava arrays de uma obra, que representam várias edições: ISBN, idioma, editora e ano podiam não pertencer à mesma edição. Agora consulta a edição diretamente por ISBN validado ou solicita explicitamente os campos da edição selecionada pela busca.
- As capas da Open Library eram solicitadas com `-L`, uma versão reduzida. No caso verificado de Outlander, ela tinha 317×500 pixels e não passava no mínimo de 400×600 do app. O endpoint original retornou 1743×2744 e passou pela validação. A resolução mínima foi mantida.

## Comportamento implementado

- Validação dos dígitos verificadores de ISBN-10/13 e conversão para a representação canônica.
- Alternativas por título, título simplificado/alias e título sem autor; apenas consultas limitadas, sem varredura do catálogo.
- Classificação conjunta por ISBN, semelhança de título, autor e idioma confirmado pela fonte. O idioma herdado do EPUB não conta como confirmação. Títulos apenas relacionados deixam de passar como o romance solicitado.
- Deduplicação por ISBN remoto; o ISBN já salvo no EPUB não é usado para fundir resultados de edições desconhecidas.
- Consultas aos provedores em paralelo, com até 19 segundos de espera conjunta. Sucesso de uma fonte é preservado quando a outra falha.
- Complementação dos três primeiros resultados em até 6,5 segundos adicionais. Edição/descrição e capa têm tarefas separadas; atualizações parciais são preservadas.
- Cache de respostas bem-sucedidas por 15 minutos, até 24 entradas; erro não é armazenado. Espaçamento das consultas à Open Library e uma tentativa adicional limitada para determinados erros transitórios.
- Downloads limitados em bytes, resolução e tempo. Capas em cache são reutilizadas por URL. Falta de capa não impede apresentar um resultado.
- Estado de progresso liberado mesmo se ocorrer uma exceção.

## Verificação

- `:androidApp:assembleDebug`, `:androidApp:testDebugUnitTest` e `:androidApp:lintRelease`: concluídos com sucesso.
- 56 testes Android, zero falhas, erros ou testes ignorados. Foram adicionados 16 testes de busca/transporte, incluindo ISBN, relevância, edições, respostas incompletas, indisponibilidade, timeout parcial, retry, cache e tamanho de resposta.
- APK atualizado instalado no Pixel 8 / API 36.
- Consultas reais: Death’s End por ISBN-13, Dust por ISBN-10 e Outlander aka Cross Stitch sem ISBN. Resultados e duração estão nos JSONs desta pasta.
- Outlander na versão final retornou três edições em aproximadamente 10,7 segundos. A primeira foi Arrow Books, 2014, ISBN 9780099599531. Duas opções trouxeram capas válidas.
- Fluxo da tela verificado: abrir busca, executar, receber resultados e cancelar. Captura `results-phone.png` e árvore `results-phone.xml`. Nenhum resultado foi aplicado.

## Limites e observações

- Google Books não tem chave configurada neste emulador. Sua consulta e interpretação foram verificadas por testes com respostas simuladas; a QA ao vivo utilizou Open Library. A ausência de chave não impede essa fonte gratuita de funcionar.
- Disponibilidade, cobertura e correção dos catálogos continuam variáveis. Campo ausente preserva o valor atual do EPUB; esse valor não deve ser interpretado como confirmação da fonte. A complementação opcional pode terminar sem imagem ou descrição nova.
- O filtro de semelhança evita falsos positivos, mas pode descartar títulos muito corrompidos ou traduzidos sem ISBN correspondente.
- A linha já existente com fonte, ano e resolução da capa quebra em várias linhas no telefone. Foi registrada na captura e mantida em respeito à instrução de não alterar o layout sem aprovação.

## Referências

- [Google Books: uso da API, autenticação e projeções](https://developers.google.com/books/docs/v1/using)
- [Open Library Search: obras e edições](https://openlibrary.org/dev/docs/api/search)
- [Open Library: limites de uso](https://openlibrary.org/developers/api)
- [Open Library Covers](https://openlibrary.org/dev/docs/api/covers); comparação entre imagem reduzida e original também verificada diretamente nas respostas HTTP durante a QA.
