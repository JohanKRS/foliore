# Capturas reais para Google Play

48 PNGs RGB de 24 bits, 1080 × 1920 (9:16): seis capturas por idioma. Todos foram capturados no APK release versionCode 2, em um único emulador isolado (`emulator-5558`, Android 16 / API 36, densidade 420 dpi), com resolução real alterada por `wm size 1080x1920` e grade de duas colunas selecionada na interface. Não houve recorte, esticamento, desenho ou retoque de interface.

| Idioma | Capturas | Arquivos e evidência |
| --- | --- | --- |
| pt-BR | 6 | [pt-BR/](pt-BR/) |
| en-US | 6 | [en-US/](en-US/) |
| es-ES | 6 | [es-ES/](es-ES/) |
| fr-FR | 6 | [fr-FR/](fr-FR/) |
| de-DE | 6 | [de-DE/](de-DE/) |
| it-IT | 6 | [it-IT/](it-IT/) |
| ja-JP | 6 | [ja-JP/](ja-JP/) |
| zh-CN | 6 | [zh-CN/](zh-CN/) |

## Sequência de cada idioma

1. `phone-01-library-light.png`: biblioteca clara, ordem de catálogo `[0,1,2,3,4,5]`.
2. `phone-02-library-dark.png`: biblioteca escura, ordem `[4,5,0,1,2,3]`, colocando as duas obras restantes no topo.
3. `phone-03-book-details.png`: editor claro da obra 0.
4. `phone-04-metadata.png`: metadados claros da obra 0, após rolagem real.
5. `phone-05-book-details-dark.png`: editor escuro da obra 4, com capa diferente.
6. `phone-06-metadata-dark.png`: metadados escuros da obra 4, após rolagem real.

Os seis EPUBs de cada conjunto contêm texto no idioma da interface. O catálogo, fontes e tradutores históricos estão em [books.json](../books.json). Os arquivos-fonte foram preservados, e a comparação dos conteúdos foi registrada. A ordem escura foi obtida pela importação de uma segunda cópia de demonstração dos mesmos livros em outra ordem; a ordenação Recentes do app permanece nativa. Nenhum dado de compra, conta ou sincronização foi simulado.

## Evidências

Cada imagem tem uma árvore de interface XML pareada. Os manifestos registram dimensões e hashes SHA-256; o manifesto geral também registra o hash do APK efetivamente instalado por tema. As imagens brutas do ADB foram preservadas em `.codex-artifacts/play-store-2026-09-13/evidence/raw-1080x1920-screencaps/`. Somente o canal alfa integralmente opaco foi removido, com igualdade dos pixels RGB verificada.

As importações clara e escura de cada idioma registram `files=6 imported=6 duplicates=0 failed=0`. Os logs, a seleção congelada por idioma, as verificações de texto e os dumps dos pacotes release estão em `.codex-artifacts/play-store-2026-09-13/evidence/`, na raiz do projeto. O tema e o idioma foram semeados no QA parado; o APK release foi instalado por cima antes de cada captura. Não houve alteração de código de produção.

A barra de status usa o modo de demonstração do Android (9:41, bateria cheia, ícone Wi-Fi sem aviso). As versões anteriores de 1080 × 2400 foram arquivadas em `.codex-artifacts/play-store-2026-09-13/previous-1080x2400-screenshots/` e não integram este conjunto final.

## Limites visuais preservados

No viewport 9:16, duas capas ficam completas na tela inicial e parte da linha seguinte aparece; os demais livros são acessíveis por rolagem. O editor nativo usa uma capa larga e sua parte inferior pode ficar fora do viewport. Os metadados foram capturados após rolagem para mostrar os campos. Títulos longos podem exceder o campo de edição de uma linha. As datas de publicação exibidas pertencem às edições digitais de origem, quando presentes, e não representam necessariamente a primeira publicação histórica.
