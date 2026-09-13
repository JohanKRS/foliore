# Capturas para upload no Google Play

Cada PNG é uma cópia exata da captura final em `../screenshots/<locale>/`, com o idioma acrescentado ao nome para distinguir os arquivos no gerenciador de recursos do Console. Só entram conjuntos completos de seis imagens PNG, RGB, em 1080 × 1920. Os originais permanecem no lugar.

Em cada idioma, carregar nesta ordem:

1. `<locale>-phone-01-library-light.png` — biblioteca clara.
2. `<locale>-phone-02-library-dark.png` — biblioteca escura, com outra ordem de livros.
3. `<locale>-phone-03-book-details.png` — capa/detalhes do primeiro livro, tema claro.
4. `<locale>-phone-04-metadata.png` — metadados do primeiro livro, tema claro.
5. `<locale>-phone-05-book-details-dark.png` — capa/detalhes de outro livro, tema escuro.
6. `<locale>-phone-06-metadata-dark.png` — metadados desse outro livro, tema escuro.

[upload-manifest.json](../upload-manifest.json) relaciona idioma, origem, cópia, texto alternativo, SHA-256 e dimensões. Seus caminhos são relativos a `artwork/play-store/`. Os textos alternativos vêm de [store-listings.json](../store-listings.json), sem alterações.

As capturas mostram bibliotecas de demonstração; os livros não estão incluídos no app. A procedência está em [LOCALIZED-SOURCES.md](../LOCALIZED-SOURCES.md).
