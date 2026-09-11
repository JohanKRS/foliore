# Correção de série e posição — 11/09/2026

A busca deixava de aproveitar etiquetas de série da obra, números nos subtítulos e formatos de catálogo como “-- Book III”, “#1”, “T3” e “V.2”. Algumas edições tinham apenas o nome da série, embora outra edição da mesma obra tivesse a posição. Registros duplicados pouco preenchidos também apareciam antes dos registros mais completos.

A extração agora considera esses formatos e consulta, quando necessário, até 50 edições ligadas à mesma obra. A preferência pelo idioma e pela série identificada evita misturar uma coleção editorial com a sequência narrativa. Posições conflitantes não são inferidas. Registros equivalentes encontrados na busca podem complementar somente a série após comparar título, autor e idioma; editora, data e ISBN da edição escolhida permanecem independentes. A pesquisa prioriza registros mais completos, preservando a prioridade do ISBN exato.

O Google Books passa a ler a associação estruturada à série e seu orderNumber, consultando o nome da série. Esse fluxo foi testado com respostas simuladas; os testes ao vivo desta rodada não usaram chave do Google Books. Internet Archive e BnF também usam a extração de série disponível em seus registros.

## Validação ao vivo

| Livro | Nome retornado pelo catálogo | Posição |
| --- | --- | --- |
| 2001: A Space Odyssey | Space Odyssey | 1 |
| 2010: Odyssey Two | Odyssey | 2 |
| 2061: Odyssey Three | Odyssey | 3 |
| 3001: The Final Odyssey | Odyssey | 4 |
| Dungeon Crawler Carl | Dungeon Crawler Carl | 1 |
| Carl's Doomsday Scenario | Dungeon Crawler Carl | 2 |
| The Dungeon Anarchist's Cookbook | Dungeon Crawler Carl | 3 |

Os nomes de série podem variar entre registros (por exemplo, “Space Odyssey” e “Odyssey”); não foi criado um mapa fixo de títulos ou inferida uma posição pelo ano no título. Os tempos em [verification.json](verification.json) são observações individuais com cache e rede variáveis, não um benchmark.

104 testes Android passaram, incluindo regressões com campos bibliográficos reais dos sete livros. Build QA e release e lint concluíram sem erros. Pela interface, série e posição apareceram marcadas, foram aplicadas e persistiram após salvar. O EPUB original e a capa desmarcada permaneceram intactos. A [prévia](series-preview.png) registra o fluxo.

## Referências

- [Open Library: busca e escolha de edição](https://openlibrary.org/dev/docs/api/search).
- [2001: edições da obra](https://openlibrary.org/works/OL17365W/editions.json?limit=50).
- [2010: edições da obra](https://openlibrary.org/works/OL17391W/editions.json?limit=50).
- [2061: edições da obra](https://openlibrary.org/works/OL17408W/editions.json?limit=50).
- [3001: edições da obra](https://openlibrary.org/works/OL17409W/editions.json?limit=50).
- [Dungeon Crawler Carl: edições](https://openlibrary.org/works/OL24593432W/editions.json?limit=50).
- [Carl’s Doomsday Scenario: edições](https://openlibrary.org/works/OL24848193W/editions.json?limit=50).
- [The Dungeon Anarchist’s Cookbook: edições](https://openlibrary.org/works/OL24848242W/editions.json?limit=50).
- [Google Books: esquema oficial de série e ordem](https://www.googleapis.com/discovery/v1/apis/books/v1/rest).

APK e hash: [release.json](release.json).
