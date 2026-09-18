# Leitor integrado

O Foliore continua focado em organizar, preparar e enviar livros. A ação secundária **Ler no Foliore**, nos detalhes do livro, abre o EPUB preparado. Isso permite ler e conferir a conversão, a capa e as alterações antes de exportar. Abrir um livro já preparado não cria outra revisão.

## Recursos

- EPUB 2 e 3, incluindo EPUBs convertidos de MOBI/AZW3 pelo importador.
- Paginação por capítulo, gestos e botões, rolagem contínua e tela cheia.
- Um toque na metade central da área de leitura (em largura e altura) exibe ou oculta os controles, também na rolagem contínua e em layout fixo. As laterais continuam virando páginas no modo paginado. Arrastar, manter o toque, selecionar texto e tocar em links não alternam a interface.
- Sumário EPUB 3 ou NCX, links internos, notas em documentos auxiliares e busca no livro inteiro com até 250 resultados.
- Marcadores com trecho de referência e exclusão individual.
- Temas papel, claro e escuro; três famílias de fontes; tamanho, entrelinha, margens, alinhamento e estilos da editora.
- Imagens, tabelas, fontes incorporadas (incluindo ofuscação IDPF), layout fixo com zoom e direção de progressão RTL.
- Adaptação automática de gravuras monocromáticas com fundo claro ao tema papel ou escuro, com opção para desligar. Capas declaradas, imagens coloridas e imagens ambíguas mantêm a aparência original.
- Toque em uma imagem para abrir o original sobre o leitor levemente escurecido, sem moldura nem botões. Use pinça ou toque duplo para ampliar e arraste para explorar. Deslize verticalmente com a imagem ajustada à tela, toque no fundo ou use Voltar para fechar, mantendo o ponto de leitura. As ações de zoom e fechar também estão disponíveis para acessibilidade.
- Brilho somente durante a leitura e opção de manter a tela ligada.
- Retomada por passagem de texto, com progresso proporcional como alternativa, preservando o lugar ao mudar a tipografia.

A busca não envia texto a servidores. Preferências visuais ficam neste aparelho; posições e marcadores acompanham a sincronização da biblioteca entre instalações do Foliore. Não há integração de progresso com Kindle, Kobo ou outros leitores externos.

## Implementação

`EpubPublication` lê recursos do ZIP sob os limites de `StorageLimits`, sem extrair o livro inteiro. O WebView do sistema renderiza um documento por vez. Não há biblioteca adicional nem serviço de leitura. A interface Compose reutiliza os diálogos do app; a visualização de imagens é uma exceção sem moldura, título ou botões.

Os recursos são interceptados em uma origem HTTPS sintética. Acesso à rede, a arquivos locais e a content providers está desativado no WebView. O HTML passa por sanitização; uma CSP libera somente o pequeno runtime do app. Scripts, áudio/vídeo e conteúdo remoto do livro não são executados. Links externos abrem uma opção explícita na interface. O arquivo preparado permanece intacto. A ofuscação de fontes segue [EPUB 3.3 §4.4](https://www.w3.org/TR/epub-33/#sec-font-obfuscation), aplicada somente em memória.

A adaptação de ilustrações analisa amostras de até 96 × 96 pixels das imagens próximas à área de leitura, durante períodos ociosos. Os resultados ficam em um cache limitado por documento; virar páginas ou mudar o tema não repete a análise do mesmo recurso. A detecção é heurística e conservadora. O efeito usa [composição CSS](https://www.w3.org/TR/compositing-1/#blending): multiplicação no papel e inversão com `screen` no escuro. Não modifica os bytes dos livros nem os arquivos exportados.

A visualização original abre um segundo WebView somente enquanto estiver visível, com o recurso local e um runtime de gestos do app. Não carrega estilos ou scripts da editora nem aplica filtros à imagem. Oferece zoom de 1× a 8×, arrasto limitado à imagem, toque duplo e fechamento por deslize vertical no enquadramento inicial, toque no fundo ou Voltar. Ao fechar, libera esse WebView. A imagem permanece aberta e se ajusta novamente quando o aparelho gira.

O leitor não remove DRM. A apresentação depende do WebView instalado e do conteúdo do EPUB; a prévia não garante paginação idêntica em um leitor externo. Busca e marcadores de texto dependem de texto real, não de páginas compostas apenas por imagens. Notas fora do spine são apresentadas em texto; notas dentro do spine seguem o link do livro. Não inclui anotações livres, tradução nem leitura em voz alta.

## Estado e sincronização

`ReadingStateRepository` persiste posição, marcadores e preferências em armazenamento privado. Cada posição tem ID do livro, hash do conteúdo, caminho do capítulo, deslocamento de texto, trecho e fração do capítulo. Eventos de restauração e mudanças de aparência não contam como nova leitura.

O campo opcional `readingState` do manifesto usa registros independentes para posição e para cada marcador. O relógio lógico avança acima dos registros observados e do relógio local. A posição mais recente vence mesmo ao voltar para uma página anterior; empates usam o ID do dispositivo. Marcadores excluídos deixam tombstones. Preferências de aparência não entram no manifesto.

Posições não alteram metadados, revisão ou status do EPUB. Uma sincronização só de leitura publica apenas um registro do diário, sem enviar os arquivos do livro. O diário mescla o estado de leitura de todos os registros retidos; clientes sem esse campo não o apagam. A coleta exige que um sucessor causal preserve esse estado. Exclusões de livros e cópias de conflito recebem o tratamento correspondente.

## Verificação

```sh
ANDROID_HOME=/caminho/do/sdk ./gradlew :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug -PconversionNativeLibraryDir=/caminho/das/bibliotecas/host
node --test scripts/test_reader_gestures.mjs scripts/test_reader_image_tones.mjs scripts/test_reader_image_viewer.mjs
```

`ReadingStateTest`, `ReadingJournalTest` e `EpubPublicationTest` verificam localização, navegação EPUB, conteúdo ativo, fontes, mesclagem, tombstones e coleta. O `SyncSafetyHarness` debug exercita dois catálogos isolados com avanço, retorno, marcador, exclusão, falha de publicação e nova tentativa, conferindo ausência de uploads do EPUB e de alterações na revisão do livro.

`test_reader_gestures.mjs` exercita os eventos do runtime distribuído, incluindo limites da área central, paginação RTL, rolagem, layout fixo com zoom, links, seleção, arrastos, toque longo, múltiplos dedos e cancelamento de gestos. A conferência no emulador complementa esses testes com os eventos reais do WebView e a interface Compose.

Gere a amostra com `python3 scripts/generate_reader_fixture.py /tmp/reader.epub`. A conferência no emulador usa esse EPUB próprio com capítulos, ilustração, tabela, links e nota. Evidências locais ficam em `.codex-artifacts/reader/`. Testes autenticados com dois aparelhos e o Drive real continuam sendo uma etapa de validação de distribuição.
