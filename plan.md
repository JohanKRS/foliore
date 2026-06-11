# Foliore — Plano completo atualizado para desenvolvimento

## 0. Instrução inicial para o Codex

Crie um app Android-first do zero, com caminho planejado para iOS, chamado provisoriamente `Foliore`.

Estruture o app como multilíngue desde o início. Mesmo que o MVP comece apenas em português, todos os textos visíveis ao usuário devem ficar preparados para localização, usando `strings.xml` no Android e evitando strings hardcoded em telas, ViewModels, fluxos de erro, botões, labels, estados vazios, menus, filtros e mensagens de progresso.

O app deve ser uma biblioteca pessoal de EPUBs, não um leitor. O foco é:

1. importar EPUBs;
2. exibir uma biblioteca bonita em grid de capas grandes;
3. extrair e editar capa/metadados;
4. gerar um EPUB preparado/limpo para envio a readers;
5. exportar/compartilhar esse EPUB;
6. permitir sincronização entre dispositivos do mesmo usuário usando uma pasta de nuvem escolhida pelo usuário, sem servidor próprio;
7. permitir que o app apareça como alvo no share sheet quando o usuário compartilhar uma imagem, para aplicar essa imagem como capa de um livro existente.

Não tente implementar tudo de uma vez. Comece pela fundação Android e pelo MVP local. O código deve ser modular, testável e com a lógica principal preparada para ser reaproveitada no iOS futuramente.

## 1. Visão do produto

O Foliore é um app local-first para organizar uma biblioteca pessoal de arquivos EPUB.

Ele não deve ser um leitor de ebooks. Ele deve funcionar como uma “estante de preparação”: o usuário importa EPUBs, corrige capa e metadados, mantém uma biblioteca visual, gera uma versão preparada do livro e envia/exporta para Kindle, Kobo, Boox, apps de arquivos, nuvem ou outro reader.

A tela principal deve parecer uma estante digital moderna: capas grandes, grid bonito, pouco ruído visual, tipografia discreta e interface leve.

O app deve ser não destrutivo. O EPUB original importado nunca deve ser alterado diretamente. Qualquer alteração de capa/metadados deve ser salva primeiro no banco local e nos arquivos auxiliares da biblioteca; quando o usuário quiser, o app gera uma cópia nova chamada `prepared.epub`.

## 2. Escopo do MVP

O MVP deve suportar apenas arquivos `.epub`.

Funcionalidades obrigatórias do MVP:

1. Criar app Android funcional.
2. Usar Kotlin Multiplatform no core compartilhado.
3. Usar Jetpack Compose + Material 3 na UI Android.
4. Criar biblioteca local com banco SQLite via SQLDelight.
5. Importar um ou mais EPUBs pelo seletor de arquivos do Android.
6. Copiar o EPUB original para o storage interno do app.
7. Calcular hash SHA-256 do arquivo importado.
8. Detectar duplicatas exatas por hash.
9. Abrir o EPUB como pacote ZIP.
10. Ler `META-INF/container.xml`.
11. Localizar o arquivo OPF.
12. Extrair metadados básicos:

    * título;
    * autor;
    * idioma;
    * identificador;
    * descrição, se existir;
    * editora, se existir;
    * data, se existir.
13. Extrair capa, quando disponível.
14. Gerar thumbnail da capa.
15. Mostrar biblioteca em grid grande de capas.
16. Abrir tela de detalhes do livro.
17. Editar metadados manualmente.
18. Trocar capa dentro do app.
19. Receber imagem compartilhada pelo sistema e aplicar como capa de um livro escolhido.
20. Gerar `prepared.epub` com metadados e capa atualizados.
21. Compartilhar/exportar `prepared.epub` usando share sheet nativo.
22. Manter o original intacto.
23. Preparar estrutura de sync por pasta, ainda que a primeira versão faça sync manual.

Funcionalidades desejáveis para depois:

1. Busca automática de metadados em Open Library.
2. Busca automática de metadados em Google Books.
3. Validação com EPUBCheck.
4. Sync automático ao abrir/fechar app.
5. Resolução avançada de conflitos.
6. Exportação em lote.
7. Importação de pasta inteira.
8. App iOS com SwiftUI.
9. Share Extension no iOS.
10. Integração mais refinada com iCloud Drive, Google Drive, Dropbox, OneDrive ou WebDAV.

Fora do escopo inicial:

1. Leitor de EPUB.
2. PDF.
3. MOBI.
4. AZW3.
5. Conversão entre formatos.
6. Remoção de DRM.
7. Editor de capítulos.
8. Alteração profunda de XHTML/CSS.
9. Sync em tempo real.
10. Servidor dedicado.
11. Conta própria do app.
12. Upload automático para Kindle por API não oficial.

## 3. Arquitetura geral

Usar a seguinte arquitetura:

* `shared/`: módulo Kotlin Multiplatform com modelos, parser EPUB, casos de uso, regras de negócio, serialização, sync, metadados, geração de EPUB preparado e interfaces de plataforma.
* `androidApp/`: app Android nativo com Jetpack Compose, Material 3, integração com Storage Access Framework, FileProvider, share sheet, intents e banco local.
* `iosApp/`: placeholder para app iOS futuro em SwiftUI, usando o mesmo core KMP, com adapters iOS próprios.

Não usar Compose Multiplatform para a UI principal. A UI deve ser nativa por plataforma.

O core compartilhado deve conter:

* modelos de domínio;
* parser EPUB;
* extração de metadados;
* extração de capa;
* geração de EPUB preparado;
* lógica de biblioteca;
* lógica de sync por manifest;
* modelos de conflito;
* providers de metadados futuros;
* interfaces de storage;
* interfaces de banco;
* interfaces de logging.

A UI Android deve conter:

* telas Compose;
* ViewModels;
* integração com SAF;
* integração com share sheet;
* Activity receptora de imagem compartilhada;
* FileProvider;
* permissões/manifesto;
* recursos de texto em `res/values/strings.xml`;
* tema visual;
* navegação.

## 4. Stack recomendada

### Shared core

Usar:

* Kotlin Multiplatform;
* Kotlinx Serialization;
* Kotlinx Coroutines;
* SQLDelight;
* Ktor Client, apenas quando iniciar busca online de metadados;
* APIs próprias de ZIP/XML, com `expect/actual` quando necessário;
* testes unitários no `commonTest`.

### Android

Usar:

* Kotlin;
* Jetpack Compose;
* Material 3;
* Navigation Compose;
* AndroidX Activity Result APIs;
* Storage Access Framework;
* FileProvider;
* Coil para carregar capas/thumbnails;
* WorkManager futuramente para sync automático;
* DataStore para preferências simples;
* SQLDelight Android driver;
* ViewModel + StateFlow.

### iOS futuro

Planejar:

* SwiftUI;
* KMP framework;
* iOS document picker/file importer;
* Share Extension;
* App Groups para compartilhar dados entre extensão e app;
* iCloud Drive como primeira opção nativa de sync;
* adapters iOS para filesystem, banco e compartilhamento.

## 5. Estrutura do repositório

Criar:

```text
epub-shelf/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  README.md

  shared/
    build.gradle.kts
    src/
      commonMain/
        kotlin/
          com/foliore/shared/
            domain/
            data/
            epub/
            library/
            sync/
            metadata/
            storage/
            utils/
      commonTest/
        kotlin/
        resources/
          epubs/
      androidMain/
        kotlin/
      androidUnitTest/
      iosMain/
        kotlin/
      iosTest/

  androidApp/
    build.gradle.kts
    src/
      main/
        AndroidManifest.xml
        res/
          drawable/
          mipmap/
          values/
            strings.xml
          xml/
            file_paths.xml
        kotlin/
          com/foliore/android/
            MainActivity.kt
            navigation/
            ui/
              theme/
              library/
              details/
              edit/
              cover/
              import/
              sync/
              settings/
            share/
            storage/
            di/
            viewmodel/

  iosApp/
    README.md
    placeholder.md

  docs/
    PRODUCT_SPEC.md
    EPUB_PIPELINE.md
    DATA_MODEL.md
    SYNC_SPEC.md
    ANDROID_SHARE_TARGET.md
    ROADMAP.md
```

## 6. Nome provisório

Nome técnico provisório:

`Foliore`

Package Android provisório:

`com.foliore.android`

Namespace shared:

`com.foliore.shared`

Não investir tempo em branding definitivo agora.

## 7. Princípios de design

A interface do Foliore deve ser leve, moderna, visual e centrada nas capas dos livros.

Prioridades:

1. Capas grandes como protagonistas.
2. Pouco ruído visual.
3. Hierarquia clara.
4. Interface calma e refinada.
5. Ações principais sempre fáceis de encontrar.
6. Status visuais discretos.
7. Boa adaptação entre mobile e tablet.
8. Sensação de curadoria, não de gerenciamento técnico.

### 7.1 Direção visual da UI

A identidade visual do Foliore deve seguir uma estética premium, editorial e minimalista. O app deve parecer uma biblioteca pessoal refinada, não um gerenciador técnico de arquivos.

A UI deve usar capas grandes como elemento principal, com linhas finas, detalhes geométricos discretos e uma composição visual calma. A interface deve transmitir organização, curadoria, cuidado e leveza.

Diretrizes visuais:

| Elemento | Direção |
|---|---|
| Capas | Grandes, protagonistas, com sombra muito sutil. |
| Linhas | Divisórias finas, quase editoriais. |
| Geometria | Pequenos marcadores, molduras, cantos, grids e detalhes de alinhamento. |
| Cores | Fundo neutro, tons calmos, poucos acentos. |
| Tipografia | Limpa, levemente editorial, sem parecer app financeiro/técnico. |
| Ícones | Lineares, finos, poucos preenchimentos. |
| Animações | Discretas, quase imperceptíveis. |
| Botões | Elegantes, baixos, com contorno fino ou preenchimento suave. |

A interface deve evitar excesso de cartões, sombras pesadas, cores fortes, gradientes chamativos ou elementos que façam o app parecer um dashboard técnico. O destaque visual deve ser sempre a coleção de capas.

Os detalhes geométricos devem aparecer como acentos: linhas de divisão, pequenos diamantes, molduras finas nas seções, cantos ornamentais discretos e indicadores de alinhamento. Esses detalhes não devem comprometer a legibilidade nem criar ruído visual.

O app deve parecer nativo no Android, mas com personalidade própria. Usar Material 3 como base estrutural, adaptando espaçamentos, ícones, botões e divisores para a estética Foliore.

## 8. Design da biblioteca

A tela principal é a biblioteca.

Elementos:

* Top bar discreta.
* Título: “Biblioteca”.
* Botão de busca.
* Botão de importar.
* Botão de sincronizar, quando sync estiver configurado.
* Menu de overflow.
* Grid de capas.

Grid:

* Telefone pequeno: 2 colunas.
* Telefone grande: 3 colunas.
* Tablet retrato: 4 ou 5 colunas.
* Tablet paisagem: 5 ou 6 colunas.
* Proporção da capa: 2:3.
* Bordas arredondadas.
* Sombra sutil.
* Placeholder elegante para livros sem capa.
* Título abaixo em no máximo 2 linhas.
* Autor menor, opcional.
* Ícone pequeno de status no canto da capa.

Estados visuais:

* Sem capa: placeholder.
* Editado mas não preparado: pequeno indicador.
* Preparado: pequeno indicador.
* Erro: pequeno indicador.
* Sync pendente: pequeno indicador discreto.
* Conflito de sync: indicador mais evidente.

## 9. Telas principais

### 9.1 Tela vazia

Quando não houver livros:

Texto principal:

“Sua biblioteca EPUB está vazia.”

Subtexto:

“Importe arquivos EPUB para organizar capas, metadados e preparar cópias limpas para seus leitores.”

Ações:

* “Importar EPUBs”
* “Configurar sincronização”, opcional e secundário.

### 9.2 Biblioteca

Funções:

* listar livros;
* buscar;
* filtrar;
* importar;
* sincronizar;
* abrir detalhes;
* entrar em seleção múltipla no long press.

Filtros iniciais:

* Todos.
* Sem capa.
* Editados.
* Preparados.
* Com erro.
* Não sincronizados.

### 9.3 Detalhes do livro

Mostrar:

* capa grande;
* título;
* autor;
* série;
* número da série;
* idioma;
* descrição;
* status;
* datas de importação/preparo/exportação/sync;
* informações técnicas resumidas.

Ações principais:

* Editar metadados.
* Trocar capa.
* Preparar EPUB.
* Compartilhar/exportar.
* Sincronizar este livro.

Ações secundárias:

* Ver informações técnicas.
* Restaurar dados extraídos do EPUB.
* Remover da biblioteca.
* Remover apenas cópia preparada.
* Abrir pasta/backup futuramente.

### 9.4 Editor de metadados

Campos:

* Título.
* Subtítulo.
* Autor(es).
* Autor para ordenação.
* Série.
* Número da série.
* Idioma.
* Editora.
* Data de publicação.
* Descrição.
* Tags.
* ISBN.
* Identificadores extras.

Ações:

* Salvar.
* Cancelar.
* Buscar metadados futuramente.
* Restaurar metadados importados.

Ao salvar:

* atualizar banco local;
* atualizar `metadata.json`;
* marcar livro como `Edited`;
* se já havia `prepared.epub`, marcar como `OutdatedPrepared`;
* marcar sync como pendente.

### 9.5 Trocar capa dentro do app

Fluxo:

1. Usuário toca em “Trocar capa”.
2. App abre seletor de imagem.
3. Usuário escolhe imagem.
4. App mostra preview.
5. App permite crop 2:3 em fase futura.
6. No MVP, redimensionar e otimizar a imagem automaticamente.
7. Salvar capa principal e thumbnail.
8. Atualizar banco.
9. Atualizar `metadata.json`.
10. Marcar `prepared.epub` como desatualizado.
11. Marcar sync pendente.

### 9.6 Receber imagem pelo share do sistema

Criar fluxo Android:

`CoverShareReceiverActivity`

O app deve aparecer como alvo quando o usuário compartilhar uma imagem de outro app.

Fluxo:

1. Usuário está em outro app e compartilha uma imagem.
2. O Android mostra o share sheet.
3. Usuário escolhe “Foliore” ou “Usar como capa no Foliore”.
4. App abre `CoverShareReceiverActivity`.
5. Activity recebe `Intent.ACTION_SEND` com MIME `image/*`.
6. Activity lê o `Uri` de `Intent.EXTRA_STREAM`.
7. App copia a imagem para cache interno.
8. App mostra tela “Escolher livro para esta capa”.
9. Usuário busca ou toca no livro.
10. App mostra preview da nova capa ao lado da capa atual.
11. Usuário confirma.
12. App aplica capa ao livro.
13. App salva nova capa e thumbnail.
14. App marca livro como `OutdatedPrepared`.
15. App marca sync pendente.
16. App mostra confirmação.

Manifesto Android esperado:

```xml
<activity
    android:name=".share.CoverShareReceiverActivity"
    android:exported="true"
    android:label="Usar como capa">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>
</activity>
```

A tela de escolha de livro deve usar o mesmo grid da biblioteca, mas com busca destacada no topo.

Estados:

* Biblioteca vazia: mostrar aviso e opção de importar EPUB.
* Imagem inválida: mostrar erro.
* Erro ao copiar imagem: mostrar erro.
* Usuário cancela: descartar cache temporário.
* Livro escolhido: aplicar.

Futuramente, também suportar `ACTION_SEND_MULTIPLE`, mas o MVP pode aceitar apenas uma imagem.

### 9.7 Preparar EPUB

Fluxo:

1. Usuário toca em “Preparar EPUB”.
2. App lê `original.epub`.
3. App localiza OPF.
4. App atualiza metadados.
5. App substitui/adiciona capa.
6. App preserva XHTML, CSS, imagens internas e ordem de leitura.
7. App reempacota EPUB.
8. App salva `prepared.epub`.
9. App roda validação básica.
10. App atualiza status para `Prepared`.
11. App marca sync pendente.
12. App oferece “Compartilhar”.

Progresso visível:

* Lendo EPUB original.
* Atualizando metadados.
* Atualizando capa.
* Gerando pacote.
* Conferindo estrutura.
* Concluído.

### 9.8 Compartilhar/exportar

Se não houver `prepared.epub`, perguntar:

“Este livro ainda não foi preparado. Deseja preparar agora?”

Depois:

* gerar se necessário;
* compartilhar usando share sheet;
* usar FileProvider;
* aplicar nome de arquivo configurável.

Padrões de nome:

* `{author} - {title}.epub`
* `{author} - {series} {seriesIndex} - {title}.epub`
* `{title}.epub`

### 9.9 Sync

Tela de sync:

* status da pasta de sync;
* última sincronização;
* itens pendentes;
* conflitos;
* botão “Sincronizar agora”;
* botão “Escolher pasta de sync”;
* botão “Desconectar sync”;
* botão “Restaurar biblioteca da pasta”.

Estados:

* Não configurado.
* Configurado.
* Sincronizando.
* Sync concluído.
* Sync com avisos.
* Sync com conflito.
* Erro de acesso à pasta.

## 10. Modelo de dados

### Book

Campos:

* `id: String`
* `originalFilePath: String`
* `preparedFilePath: String?`
* `coverImagePath: String?`
* `coverThumbPath: String?`
* `originalHashSha256: String`
* `fileSizeBytes: Long`
* `importedAt: Instant`
* `updatedAt: Instant`
* `lastPreparedAt: Instant?`
* `lastExportedAt: Instant?`
* `lastSyncedAt: Instant?`
* `status: BookStatus`
* `syncStatus: SyncStatus`
* `revision: Long`
* `updatedByDeviceId: String`
* `technicalStatus: String?`

### BookStatus

Valores:

* `IMPORTED`
* `NEEDS_REVIEW`
* `EDITED`
* `PREPARED`
* `OUTDATED_PREPARED`
* `ERROR`

### SyncStatus

Valores:

* `LOCAL_ONLY`
* `SYNCED`
* `PENDING_UPLOAD`
* `PENDING_DOWNLOAD`
* `CONFLICT`
* `SYNC_ERROR`

### BookMetadata

Campos:

* `bookId: String`
* `title: String`
* `subtitle: String?`
* `authorsJson: String`
* `sortAuthor: String?`
* `language: String?`
* `publisher: String?`
* `publishedDate: String?`
* `description: String?`
* `tagsJson: String`
* `isbn: String?`
* `identifiersJson: String`
* `seriesName: String?`
* `seriesIndex: Double?`
* `source: MetadataSource`

### MetadataSource

Valores:

* `EPUB`
* `MANUAL`
* `OPEN_LIBRARY`
* `GOOGLE_BOOKS`
* `MIXED`

### EpubTechnicalInfo

Campos:

* `bookId: String`
* `epubVersion: String?`
* `opfPath: String`
* `navPath: String?`
* `ncxPath: String?`
* `coverHref: String?`
* `packageIdentifier: String?`
* `hasNav: Boolean`
* `hasNcx: Boolean`
* `manifestItemCount: Long`
* `spineItemCount: Long`
* `warningsJson: String`
* `errorsJson: String`

### PreparedArtifact

Campos:

* `id: String`
* `bookId: String`
* `filePath: String`
* `createdAt: Instant`
* `fileSizeBytes: Long`
* `metadataSnapshotJson: String`
* `preparationWarningsJson: String`
* `preparationErrorsJson: String`
* `isValidBasicStructure: Boolean`

### DeviceInfo

Campos:

* `deviceId: String`
* `deviceName: String`
* `platform: String`
* `createdAt: Instant`

O `deviceId` deve ser gerado no primeiro uso e persistido localmente.

Exemplo:

```json
{
  "deviceId": "android-phone-a13f",
  "deviceName": "Galaxy S24",
  "platform": "android"
}
```

## 11. Estrutura de arquivos local

No storage interno do app:

```text
/app-data/
  /books/
    /{bookId}/
      original.epub
      prepared.epub
      cover_original.jpg
      cover_thumb.jpg
      metadata.json
      technical.json
      changes.jsonl
  /cache/
    /incoming-covers/
  /exports/
  /snapshots/
    library-2026-06-08T21-30-00.sqlite
```

Regras:

* `original.epub` nunca é alterado.
* `prepared.epub` pode ser regenerado.
* `metadata.json` representa o estado portável do livro.
* `technical.json` guarda dados extraídos do EPUB.
* `changes.jsonl` pode registrar mudanças para debug/sync futuro.
* thumbnails são derivados e podem ser regenerados.

## 12. Estrutura de sync sem servidor

O usuário escolhe uma pasta de sync. No Android, isso deve ser feito por seletor de árvore de documentos. O app deve persistir acesso à pasta, quando possível.

Estrutura da pasta de sync:

```text
/Foliore Library/
  library.manifest.json
  /devices/
    android-phone-a13f.json
    android-tablet-91bc.json
  /books/
    /{bookId}/
      original.epub
      prepared.epub
      cover_original.jpg
      cover_thumb.jpg
      metadata.json
      technical.json
      changes.jsonl
  /snapshots/
    library-2026-06-08T21-30-00.sqlite
```

Importante:

* Não sincronizar um SQLite vivo como fonte principal.
* O banco local é cache/índice rápido.
* A fonte portável da biblioteca são os arquivos por livro + `library.manifest.json`.
* O banco local deve ser reconstruível a partir da pasta de sync.

## 13. Manifesto de sync

Arquivo:

`library.manifest.json`

Exemplo:

```json
{
  "schemaVersion": 1,
  "libraryId": "uuid-da-biblioteca",
  "updatedAt": "2026-06-08T21:30:00Z",
  "updatedByDeviceId": "android-phone-a13f",
  "books": [
    {
      "bookId": "uuid-do-livro",
      "revision": 7,
      "updatedAt": "2026-06-08T21:29:00Z",
      "updatedByDeviceId": "android-phone-a13f",
      "originalHashSha256": "abc123",
      "metadataFile": "books/uuid-do-livro/metadata.json",
      "originalFile": "books/uuid-do-livro/original.epub",
      "preparedFile": "books/uuid-do-livro/prepared.epub",
      "coverFile": "books/uuid-do-livro/cover_original.jpg",
      "deleted": false
    }
  ]
}
```

Cada livro deve ter `revision`.

Regra simples:

* Se o item remoto tem `revision` maior que o local, baixar/aplicar remoto.
* Se o item local tem `revision` maior que o remoto, enviar local.
* Se ambos foram alterados desde o último sync e têm revisões divergentes, marcar conflito.
* Se hashes são iguais, deduplicar.
* Se um livro foi deletado, usar tombstone em vez de apagar imediatamente.

## 14. Metadata JSON por livro

Arquivo:

`books/{bookId}/metadata.json`

Exemplo:

```json
{
  "schemaVersion": 1,
  "bookId": "uuid",
  "revision": 7,
  "updatedAt": "2026-06-08T21:30:00Z",
  "updatedByDeviceId": "android-phone-a13f",
  "title": "Book Title",
  "subtitle": null,
  "authors": ["Author Name"],
  "sortAuthor": "Name, Author",
  "language": "en",
  "publisher": null,
  "publishedDate": null,
  "description": null,
  "tags": [],
  "isbn": null,
  "identifiers": {},
  "seriesName": null,
  "seriesIndex": null,
  "originalHashSha256": "abc123",
  "status": "OUTDATED_PREPARED"
}
```

## 15. Sync manual MVP

No MVP, implementar sync manual:

Botão:

“Sincronizar agora”

Fluxo:

1. Verificar se pasta de sync está configurada.
2. Ler `library.manifest.json`.
3. Comparar manifesto remoto com banco local.
4. Detectar novos livros remotos.
5. Detectar livros locais pendentes de upload.
6. Detectar conflitos.
7. Aplicar downloads.
8. Aplicar uploads.
9. Atualizar manifesto.
10. Atualizar `lastSyncedAt`.
11. Mostrar resultado.

Não implementar sync em background no primeiro momento.

## 16. Sync automático futuro

Depois do sync manual funcionar:

* sincronizar ao abrir o app;
* sincronizar ao voltar para foreground;
* sincronizar após importar livro;
* sincronizar após alterar capa/metadados;
* sincronizar apenas em Wi-Fi, se configurado;
* usar WorkManager no Android.

## 17. Conflitos

Conflitos esperados:

1. Mesmo livro editado em dois dispositivos.
2. Capa alterada em dois dispositivos.
3. Livro removido em um dispositivo e editado em outro.
4. Mesmo EPUB importado em dois dispositivos.
5. Manifesto remoto alterado enquanto sync estava em andamento.

Solução MVP:

* conflito marca livro como `CONFLICT`;
* tela simples mostra:

  * versão local;
  * versão remota;
  * data de alteração;
  * dispositivo;
* usuário escolhe:

  * manter local;
  * usar remoto;
  * duplicar como novo livro.

Para capa:

* mostrar preview da capa local e remota.

Para metadados:

* mostrar campos diferentes.

Não tentar merge automático complexo no MVP.

## 18. EPUB parser

Módulo:

`shared/epub`

Classes sugeridas:

* `EpubArchive`
* `EpubContainerParser`
* `EpubPackageParser`
* `EpubMetadataExtractor`
* `EpubCoverExtractor`
* `EpubNavInspector`
* `EpubPreparationWriter`
* `EpubBasicValidator`
* `EpubZipWriter`

O parser deve ser tolerante.

Importação mínima:

1. Validar extensão `.epub`.
2. Abrir como ZIP.
3. Verificar `mimetype`.
4. Ler `META-INF/container.xml`.
5. Localizar OPF.
6. Parsear OPF.
7. Extrair metadata.
8. Extrair manifest.
9. Extrair spine.
10. Localizar capa:

    * item com `properties="cover-image"`;
    * meta antiga `name="cover"`;
    * heurística por nome de arquivo;
    * fallback: sem capa.
11. Localizar nav:

    * item com `properties="nav"`;
    * fallback para NCX antigo.
12. Gerar `EpubTechnicalInfo`.

## 19. ImportEpubUseCase

Entrada:

* URI ou stream do arquivo;
* nome original do arquivo.

Processo:

1. Conferir se é `.epub`.
2. Copiar para arquivo temporário.
3. Calcular SHA-256.
4. Checar duplicata.
5. Criar `bookId`.
6. Criar pasta `/books/{bookId}/`.
7. Copiar temporário para `original.epub`.
8. Abrir EPUB.
9. Extrair metadados.
10. Extrair capa.
11. Gerar thumbnail.
12. Criar `metadata.json`.
13. Criar `technical.json`.
14. Salvar no banco.
15. Marcar como `IMPORTED`, `NEEDS_REVIEW` ou `ERROR`.
16. Marcar sync como `PENDING_UPLOAD`, se sync estiver configurado.

Resultado:

* `Success(bookId)`;
* `Duplicate(existingBookId)`;
* `ImportError(reason)`.

## 20. ReplaceBookCoverUseCase

Entrada:

* `bookId`;
* imagem local/cache;
* origem:

  * `IN_APP_PICKER`;
  * `ANDROID_SHARE_TARGET`;
  * `FUTURE_METADATA_PROVIDER`.

Processo:

1. Validar imagem.
2. Decodificar imagem.
3. Corrigir orientação, se necessário.
4. Redimensionar para tamanho máximo configurado.
5. Gerar capa principal.
6. Gerar thumbnail.
7. Salvar arquivos na pasta do livro.
8. Atualizar banco.
9. Atualizar `metadata.json`.
10. Incrementar `revision`.
11. Atualizar `updatedAt`.
12. Marcar `updatedByDeviceId`.
13. Se existe `prepared.epub`, marcar `OUTDATED_PREPARED`.
14. Marcar sync `PENDING_UPLOAD`.

## 21. UpdateBookMetadataUseCase

Entrada:

* `bookId`;
* campos editados.

Processo:

1. Validar título não vazio.
2. Normalizar autor(es).
3. Atualizar banco.
4. Atualizar `metadata.json`.
5. Incrementar `revision`.
6. Marcar `updatedAt`.
7. Marcar `updatedByDeviceId`.
8. Se existe `prepared.epub`, marcar `OUTDATED_PREPARED`.
9. Marcar sync `PENDING_UPLOAD`.

## 22. PrepareEpubUseCase

Entrada:

* `bookId`;
* opções de preparo.

Processo:

1. Abrir `original.epub`.
2. Copiar pacote para área temporária ou reescrever ZIP item por item.
3. Localizar OPF.
4. Atualizar metadados:

   * título;
   * autores;
   * idioma;
   * identificadores;
   * descrição;
   * editora;
   * data;
   * série;
   * data de modificação.
5. Atualizar capa:

   * adicionar ou substituir imagem de capa;
   * garantir item no manifest;
   * garantir propriedade de capa;
   * manter compatibilidade com metadado antigo de capa se necessário.
6. Preservar spine.
7. Preservar nav.
8. Preservar NCX, se existir.
9. Não reescrever XHTML.
10. Não limpar CSS no MVP.
11. Não alterar imagens internas, exceto capa.
12. Reempacotar EPUB:

* `mimetype` primeiro;
* `mimetype` sem compressão;
* demais arquivos comprimidos.

13. Salvar `/books/{bookId}/prepared.epub`.
14. Rodar validação básica.
15. Atualizar status para `PREPARED`.
16. Atualizar `lastPreparedAt`.
17. Atualizar `metadata.json`.
18. Marcar sync como `PENDING_UPLOAD`.

## 23. Validação básica

Criar `EpubBasicValidator`.

Checar:

* existe `mimetype`;
* conteúdo de `mimetype` é correto;
* existe `META-INF/container.xml`;
* container aponta para OPF existente;
* OPF é XML válido;
* metadata existe;
* manifest existe;
* spine existe;
* itens do spine existem no manifest;
* capa declarada existe no pacote;
* nav existe quando declarado;
* NCX existe quando declarado.

Resultado:

* `VALID`;
* `VALID_WITH_WARNINGS`;
* `INVALID`.

Não bloquear importação por warnings. Bloquear apenas quando impossível localizar OPF ou ler estrutura mínima.

## 24. Exportação e compartilhamento

Criar `ExportPreparedEpubUseCase`.

No Android:

1. Verificar se `prepared.epub` existe e está atualizado.
2. Se não existe ou está desatualizado, perguntar se deve preparar.
3. Criar nome amigável.
4. Compartilhar via FileProvider.
5. Usar share sheet.
6. Atualizar `lastExportedAt`.

Nomes de arquivo devem sanitizar caracteres inválidos.

Exemplos:

```text
Author - Title.epub
Author - Series 01 - Title.epub
Title.epub
```

## 25. Android share target para imagem de capa

Criar pacote:

`com.foliore.android.share`

Classes:

* `CoverShareReceiverActivity`
* `CoverShareViewModel`
* `IncomingCoverRepository`
* `IncomingCoverScreen`
* `ChooseBookForCoverScreen`

Fluxo técnico:

1. Activity recebe intent.
2. Verifica action:

   * `Intent.ACTION_SEND`
3. Verifica type:

   * `image/*`
4. Obtém URI:

   * `Intent.EXTRA_STREAM`
5. Copia para cache interno:

   * `/cache/incoming-covers/{uuid}.jpg`
6. Abre tela de seleção de livro.
7. Ao selecionar livro, chama `ReplaceBookCoverUseCase`.
8. Limpa cache temporário.
9. Mostra confirmação.
10. Finaliza Activity ou leva para detalhes do livro.

UX:

* título: “Usar imagem como capa”
* subtítulo: “Escolha o livro que receberá esta capa.”
* mostrar preview da imagem recebida no topo, em formato pequeno.
* permitir busca na biblioteca.
* mostrar grid/lista de livros.
* confirmação com capa antiga vs nova.

## 26. Android import target para EPUB futuro

Futuramente, o app também pode aparecer quando o usuário compartilhar um EPUB.

Não implementar agora, mas projetar:

* aceitar `application/epub+zip`;
* aceitar `application/octet-stream` com nome `.epub`, com cuidado;
* fluxo direto para importação.

## 27. Settings

Tela de configurações:

Seções:

### Aparência

* Tema:

  * seguir sistema;
  * claro;
  * escuro.
* Densidade do grid:

  * confortável;
  * compacta;
  * grande.

### Biblioteca

* Padrão de nome para exportação.
* Qualidade da capa preparada.
* Tamanho máximo da capa.
* Preservar original: sempre ligado e bloqueado.

### Sincronização

* Escolher pasta de sync.
* Sincronizar agora.
* Última sincronização.
* Sync automático: futuro.
* Apenas Wi-Fi: futuro.
* Desconectar pasta de sync.

### Avançado

* Exportar logs.
* Exportar snapshot do banco.
* Reconstruir banco a partir da pasta de sync.
* Limpar cache.

## 28. Roadmap atualizado

### Milestone 1 — Fundação Android/KMP

Objetivo: app abre, mostra tela vazia e estrutura base está criada.

Tarefas:

1. Criar projeto KMP.
2. Criar `shared`.
3. Criar `androidApp`.
4. Configurar Compose Material 3.
5. Configurar Navigation Compose.
6. Configurar SQLDelight.
7. Criar tema inicial.
8. Criar tela vazia.
9. Criar arquitetura de ViewModel/StateFlow.
10. Criar modelos principais no shared.

Critério de aceite:

* app instala;
* app abre;
* mostra biblioteca vazia;
* não há lógica EPUB ainda.

### Milestone 2 — Importação local EPUB

Objetivo: importar EPUB e salvar na biblioteca.

Tarefas:

1. Criar botão “Importar EPUB”.
2. Abrir seletor de arquivos.
3. Copiar EPUB para storage interno.
4. Calcular SHA-256.
5. Detectar duplicata.
6. Criar parser mínimo.
7. Ler `container.xml`.
8. Localizar OPF.
9. Extrair título/autor.
10. Salvar no banco.

Critério de aceite:

* usuário importa EPUB;
* app salva original;
* app extrai título/autor quando disponíveis;
* app detecta duplicata.

### Milestone 3 — Capas e grid visual

Objetivo: biblioteca bonita com capas grandes.

Tarefas:

1. Extrair capa do EPUB.
2. Gerar thumbnail.
3. Criar `BookGrid`.
4. Criar `BookCoverCard`.
5. Criar placeholder de capa.
6. Exibir título/autor.
7. Criar tela de detalhes.

Critério de aceite:

* livros aparecem em grid grande;
* capas aparecem quando disponíveis;
* toque abre detalhes.

### Milestone 4 — Edição manual

Objetivo: editar metadados e capa dentro do app.

Tarefas:

1. Criar tela de edição de metadados.
2. Salvar alterações.
3. Criar fluxo de trocar capa.
4. Salvar nova capa.
5. Atualizar thumbnail.
6. Marcar status como editado.
7. Marcar sync pendente, mesmo que sync ainda não esteja ativo.

Critério de aceite:

* usuário edita título/autor/série;
* usuário troca capa;
* alterações persistem após fechar app.

### Milestone 5 — Receber capa pelo share do Android

Objetivo: app aparece como alvo quando usuário compartilha imagem.

Tarefas:

1. Criar `CoverShareReceiverActivity`.
2. Adicionar intent-filter `image/*`.
3. Receber URI da imagem.
4. Copiar imagem para cache.
5. Mostrar tela “Escolher livro”.
6. Aplicar imagem como capa.
7. Limpar cache.
8. Marcar livro como `OUTDATED_PREPARED`.

Critério de aceite:

* usuário compartilha uma imagem de outro app;
* escolhe Foliore;
* escolhe um livro;
* capa é atualizada.

### Milestone 6 — Gerar EPUB preparado

Objetivo: gerar `prepared.epub`.

Tarefas:

1. Criar `PrepareEpubUseCase`.
2. Atualizar OPF com metadados.
3. Inserir/substituir capa.
4. Preservar spine/nav/conteúdo.
5. Reempacotar EPUB corretamente.
6. Rodar validação básica.
7. Salvar `prepared.epub`.
8. Atualizar status.

Critério de aceite:

* usuário toca em preparar;
* app gera EPUB novo;
* original permanece intacto;
* prepared.epub contém capa/metadados atualizados.

### Milestone 7 — Compartilhar/exportar

Objetivo: enviar EPUB preparado para outros apps.

Tarefas:

1. Configurar FileProvider.
2. Criar `file_paths.xml`.
3. Criar share intent.
4. Nomear arquivo exportado.
5. Atualizar `lastExportedAt`.

Critério de aceite:

* usuário compartilha `prepared.epub`;
* Android share sheet abre;
* apps compatíveis recebem o arquivo.

### Milestone 8 — Sync manual por pasta

Objetivo: permitir sync entre dispositivos usando pasta escolhida pelo usuário.

Tarefas:

1. Criar tela de sync.
2. Permitir escolher pasta via SAF.
3. Criar estrutura `/Foliore Library/`.
4. Criar `library.manifest.json`.
5. Exportar livros locais para pasta.
6. Importar livros remotos para banco local.
7. Comparar revisões.
8. Detectar conflitos simples.
9. Implementar botão “Sincronizar agora”.

Critério de aceite:

* dispositivo A exporta biblioteca para pasta de sync;
* dispositivo B aponta para mesma pasta;
* dispositivo B importa biblioteca;
* alterações feitas em A aparecem em B após sync manual.

### Milestone 9 — Polimento

Objetivo: deixar o app agradável e confiável.

Tarefas:

1. Melhorar estados de erro.
2. Melhorar animações.
3. Melhorar grid em tablets.
4. Adicionar filtros.
5. Adicionar seleção múltipla.
6. Adicionar logs exportáveis.
7. Adicionar testes com EPUBs reais.
8. Ajustar performance de thumbnails.

Critério de aceite:

* app parece leve e estável;
* grid é agradável em celular e tablet;
* erros são compreensíveis.

### Milestone 10 — Metadados online

Objetivo: buscar capa/metadados automaticamente.

Tarefas:

1. Criar interface `MetadataProvider`.
2. Implementar Open Library.
3. Implementar Google Books.
4. Criar tela de comparação.
5. Aplicar campos selecionados.
6. Baixar capa remota.

Critério de aceite:

* usuário busca metadados;
* app mostra candidatos;
* usuário aplica resultado.

### Milestone 11 — iOS

Objetivo: iniciar app iOS nativo usando o shared core.

Tarefas:

1. Criar app SwiftUI.
2. Integrar framework KMP.
3. Criar tela de biblioteca iOS.
4. Implementar importação de EPUB via document picker.
5. Implementar detalhes.
6. Implementar troca de capa.
7. Implementar export/share.
8. Implementar sync com pasta/iCloud.
9. Implementar Share Extension para receber imagem.
10. Usar App Groups para compartilhar imagem recebida entre extensão e app.

Critério de aceite:

* iOS importa EPUB;
* mostra biblioteca;
* reaproveita parser/modelos/use cases;
* interface é nativa SwiftUI.

## 29. Testes

Criar fixtures:

```text
shared/src/commonTest/resources/epubs/
  valid_epub3_with_cover.epub
  valid_epub2_with_ncx.epub
  no_cover.epub
  opf_in_subfolder.epub
  bad_metadata.epub
  missing_container.epub
  zip_not_epub.zip
```

Testes obrigatórios:

* localizar OPF;
* extrair título;
* extrair autor;
* extrair idioma;
* extrair capa;
* detectar EPUB sem capa;
* detectar duplicata por hash;
* atualizar OPF;
* reempacotar com `mimetype` correto;
* validar prepared.epub básico;
* serializar/deserializar `metadata.json`;
* comparar manifesto local/remoto;
* detectar conflito de revisão.

## 30. Regras de código

* Não colocar lógica EPUB em Composable.
* Não colocar lógica de arquivo diretamente em ViewModel quando puder estar em use case/repository.
* Não hardcodar textos visíveis ao usuário em Composables, Activities, ViewModels ou use cases.
* Centralizar textos Android em `res/values/strings.xml`, incluindo labels, botões, menus, filtros, mensagens de erro, estados vazios, progresso, diálogos e textos do share target.
* Usar `stringResource`, placeholders, `plurals` e recursos equivalentes sempre que houver texto dinâmico ou contagem.
* Operações pesadas fora da main thread.
* UI baseada em estado imutável.
* Resultados de use case devem ser explícitos.
* Erros devem ter mensagens técnicas e mensagens amigáveis.
* Manter original intacto sempre.
* Criar interfaces para plataforma quando necessário.
* Preparar core para iOS desde o começo.
* Escrever testes do parser antes de expandir funcionalidades.

## 31. Primeira entrega esperada do Codex

A primeira entrega deve parar no fim da Milestone 2 ou início da Milestone 3.

Entregar:

1. Projeto KMP criado.
2. App Android abre.
3. UI inicial em Compose.
4. SQLDelight configurado.
5. Seletor de EPUB funcionando.
6. Arquivo copiado para storage interno.
7. SHA-256 calculado.
8. Parser mínimo localiza OPF.
9. Título e autor extraídos.
10. Livro salvo no banco.
11. Livro mostrado em lista/grid inicial, ainda que sem polimento final.

Não implementar sync, geração de EPUB preparado ou share target na primeira entrega se isso atrasar a fundação.

## 32. Critérios de aceite do MVP completo

O MVP completo é aceito quando:

1. Usuário instala o app Android.
2. Usuário importa EPUB.
3. Livro aparece no grid com capa grande.
4. Usuário abre detalhes.
5. Usuário edita metadados.
6. Usuário troca capa dentro do app.
7. Usuário compartilha uma imagem de outro app para o Foliore.
8. App pergunta qual livro deve receber a capa.
9. Capa é aplicada.
10. Usuário gera `prepared.epub`.
11. Usuário compartilha/exporta `prepared.epub`.
12. Original permanece intacto.
13. Usuário configura uma pasta de sync.
14. Usuário sincroniza manualmente.
15. Outro dispositivo consegue reconstruir/importar a biblioteca a partir da mesma pasta.
16. Conflitos simples são detectados, não sobrescritos silenciosamente.

## 33. Resumo final do produto

O Foliore deve ser um app bonito, leve e local-first para organizar EPUBs pessoais. Ele deve destacar capas em uma estante visual grande e elegante, permitir corrigir metadados e capas, gerar cópias EPUB limpas/preparadas para readers, receber imagens de capa pelo share do sistema e sincronizar a biblioteca entre dispositivos usando uma pasta de nuvem escolhida pelo próprio usuário.

A prioridade não é ler ebooks. A prioridade é curar, organizar, preparar, exportar e manter backup/sync da biblioteca.
