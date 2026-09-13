# Capturas reais — en-US

Seis capturas de 13/09/2026, feitas com o APK release versionCode 2 no emulador isolado `emulator-5558` (Android 16 / API 36, 1080 × 1920 px, 420 dpi). Grade de **duas colunas**, escolhida pela interface nativa.

- `phone-01-library-light.png`: biblioteca clara no início da lista.
- `phone-02-library-dark.png`: biblioteca escura com a ordem Recentes rotacionada, começando pelas duas obras que ficavam no fim da biblioteca clara.
- `phone-03-book-details.png`: editor real de Little Women, mostrando a capa.
- `phone-04-metadata.png`: mesmo editor após rolagem, mostrando metadados.
- `phone-05-book-details-dark.png`: editor escuro de Don Quixote, obra diferente da captura clara.
- `phone-06-metadata-dark.png`: metadados dessa obra no modo escuro.

Biblioteca com seis textos efetivamente em `en`: Little Women, Frankenstein, Alice’s Adventures in Wonderland, The Adventures of Sherlock Holmes, Don Quixote, Faust — Part One. A tela inicial mostra duas capas completas e parte da linha seguinte; os outros livros são acessíveis por rolagem. A captura representa o viewport real 9:16, sem recorte ou esticamento. As fontes, traduções históricas e créditos estão registrados em `../../books.json` e na proposta de proveniência. Os textos-fonte e avisos foram preservados; só título, nome de exibição do autor e capa dos EPUBs de demonstração foram normalizados.

Os livros foram importados usando o importador real do APK QA; depois o APK release foi instalado por cima, preservando os dados. Para a biblioteca escura, uma segunda cópia das mesmas seis obras foi importada em ordem diferente, usando a ordenação Recentes nativa e sem alterar o código do app. A ordem escura é [4,5,0,1,2,3] em relação à clara. As imagens finais vieram de `adb exec-out screencap -p` no **release**, sem indicador DEBUGGABLE. Todas têm uma árvore XML correspondente e hashes em `manifest.json`. Foi removido somente o canal alfa integralmente opaco, com igualdade dos pixels RGB verificada, resultando em PNG RGB de 24 bits. Nenhum pixel visível ou estado de compra/sincronização foi retocado. A barra de status usa o modo de demonstração do Android (9:41, bateria cheia), sem conta pessoal.

Logs de importação (`imported=6 duplicates=0 failed=0`), catálogo congelado por idioma, verificação dos textos, metadados do pacote e screencaps brutos estão em `.codex-artifacts/play-store-2026-09-13/evidence/`, na raiz do projeto. A data de publicação do editor é a do EPUB de origem, quando presente; ela não pretende representar a primeira publicação histórica da obra.
