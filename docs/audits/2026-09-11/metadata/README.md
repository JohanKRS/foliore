# Busca aberta de metadados — 11/09/2026

Foram adicionados Internet Archive e BnF à Open Library e ao Google Books (quando há chave configurada). As fontes são consultadas em paralelo, com limites de tempo e aproveitamento dos resultados das fontes que responderam. Nenhum EPUB é enviado: as consultas usam os dados bibliográficos da busca.

## Comportamento

- A abertura do popup inicia a busca. Há nova tentativa quando não há resultados, sem botão redundante na abertura nem aviso de configuração do Google Books.
- O seletor apresenta até 12 resultados e carrega os detalhes da edição escolhida, inclusive fora dos primeiros resultados.
- ISBN, título, autor e idioma orientam a relevância. Adaptações e títulos relacionados incompatíveis são filtrados. Digitalizações com vários ISBNs não são mescladas indiscriminadamente com edições de catálogo.
- Fontes com o mesmo ISBN único e idiomas compatíveis podem complementar campos ausentes. Dados indisponíveis não são inventados.
- Os campos disponíveis começam marcados; a capa começa desmarcada. Campos desmarcados ou ausentes preservam o conteúdo local. Identificadores selecionados são incorporados sem apagar os locais.
- Idiomas aparecem por extenso e localizados, conservando os códigos próprios para EPUB. A data conserva ano, mês e dia quando disponíveis, normalizados para o formato de data do EPUB.
- A capa da Open Library usa a URL de imagem grande corrigida; imagens disponíveis podem ser menores e têm suas dimensões exibidas. O usuário decide aplicar a capa.

## Validação

87 testes Android e 10 testes compartilhados passaram. Build QA e release e lint concluíram; lint tem 119 avisos e 5 sugestões, sem erros. Foram cobertos falhas de fontes, prazo de resposta, isolamento de edições, idioma, seleção de campos, ISBN, datas e rejeição de entidades XML externas. Não foi feito um teste ao vivo do Google Books com chave nesta rodada; seu fluxo foi validado com respostas simuladas.

No emulador isolado foram usados três EPUBs sintéticos com os dados bibliográficos de Dom Casmurro, Dune e Le Petit Prince. As três buscas ofereceram 12 resultados. Dune passou a priorizar o ISBN 9780441172719, Ace Books, 1987, com descrição e capa. Em Le Petit Prince, salvar manteve a data 2007-03 e o código fr; série e descrição desmarcadas permaneceram intactas. A capa disponível começou desmarcada e só foi aplicada após a seleção. Os hashes dos três EPUBs originais foram preservados.

A interface foi conferida em dimensões de telefone e tablet. Detalhes em [verification.json](verification.json), [campos no telefone](phone-fields.png) e [resultados no tablet](tablet-results.png). Os catálogos podem conter dados incompletos ou incorretos; o usuário continua escolhendo a edição e os campos antes de aplicar.

## Fontes oficiais

- [Open Library — busca](https://openlibrary.org/dev/docs/api/search) e [capas](https://openlibrary.org/dev/docs/api/covers).
- [Internet Archive — APIs](https://archive.org/developers/).
- [BnF — serviço SRU do catálogo geral](https://www.bnf.fr/fr/service-sru-catalogue-general-de-la-bnf).

O APK desta rodada e seu hash estão em [release.json](release.json). A instalação substitui o app existente, preservando seus dados. Não houve publicação na Play Store.
