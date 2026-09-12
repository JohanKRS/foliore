# Créditos e licenças distribuídos no Android

Revisão: 12/09/2026. O link **Sobre** nas configurações apresenta versão, fontes de metadados, integrações, tipografia e licenças. O conteúdo jurídico dos componentes é legível offline; os links dos serviços abrem seus termos atuais.

## Escopo do inventário

O catálogo em `androidApp/src/main/assets/licenses/catalog.json` cobre **127 coordenadas Maven distintas** resolvidas em `releaseRuntimeClasspath`, agrupadas em 15 projetos, mais a fonte Cormorant Garamond. Inclui dependências transitivas do módulo compartilhado. Não inclui ferramentas de compilação nem dependências exclusivas de testes/debug.

- Os POMs das bibliotecas abertas declaram Apache-2.0. `listenablefuture:1.0` herda essa licença do POM pai do Guava.
- Os sete artefatos Google Play services declaram a licença do Android SDK. Seus `third_party_licenses.json` e `.txt` fornecem avisos adicionais; o gerador preserva os trechos indicados pelos offsets **em bytes**, incluindo textos diferentes para o mesmo projeto.
- Os arquivos LICENSE/NOTICE presentes nos JARs/AARs e em `classes.jar` são extraídos sem alteração. Textos idênticos são deduplicados por SHA-256, mantendo os títulos e as origens no catálogo.
- O NOTICE do OkHttp inclui a Public Suffix List sob MPL-2.0. Distribuímos essa licença e os dados legíveis das regras/exceções correspondentes ao arquivo comprimido do artefato utilizado.
- A fonte empacotada mantém seu copyright e a SIL Open Font License 1.1 já distribuídos pelo projeto. Os ícones vêm de Material Icons/Jetpack Compose; a fonte dos demais textos é fornecida pelo Android.
- Não foi atribuída uma licença à aplicação Foliore como um todo. As licenças aqui documentadas pertencem aos componentes de terceiros.

## Atualização

1. Atualize as dependências e execute `./gradlew :androidApp:exportThirdPartyInventory`.
2. Confira as versões, POMs, licenças e avisos dos artefatos alterados. Atualize `projects.json` se necessário; não trate um grupo Maven como garantia permanente de licença.
3. Execute `python3 scripts/third_party_notices.py`.
4. Revise o diff dos textos e de `catalog.json`, execute os testes de `ThirdPartyCatalogTest` e gere o APK.

`verifyThirdPartyNotices` é executada por `preBuild` e compara as coordenadas resolvidas com as documentadas. Os testes conferem os arquivos e seus hashes. O gerador usa os artefatos/POMs já resolvidos pelo Gradle, sem buscar licenças na rede durante builds normais. Fontes suplementares estão em `supplemental-sources.json`.

## Fontes e integrações

- [Open Library — condições do catálogo](https://openlibrary.org/developers/licensing): não presume que capas ou contribuições individuais estejam livres de direitos. Crédito ao Internet Archive e à comunidade.
- [Internet Archive — termos](https://archive.org/about/terms): direitos e condições podem variar por item. O app consulta registros e imagens; não incorpora o código do servidor Open Library/Internet Archive.
- [BnF — reutilização de metadados](https://www.bnf.fr/fr/conditions-de-reutilisations-des-donnees-de-la-bnf): Licence Ouverte / Open Licence (Etalab), com identificação da fonte e data de recuperação. A licença dos metadados não deve ser confundida com as condições de digitalizações Gallica.
- [Google Books — termos](https://developers.google.com/books/terms) e [atribuição](https://developers.google.com/books/branding): integração opcional com chave do usuário. Crédito no “Sobre” não substitui requisitos junto aos resultados.
- [Google Play services — avisos de terceiros](https://developers.google.com/android/guides/opensource) e [Android SDK — termos](https://developer.android.com/studio/terms).
- [Google APIs — termos](https://developers.google.com/terms), para a integração Drive.
- [OpenAI — contrato de serviços de API](https://openai.com/policies/services-agreement/).
- [Send to Kindle](https://www.amazon.com/sendtokindle): envio de arquivo pelo servidor SMTP escolhido pelo usuário, sem SDK Kindle incorporado.

## Pendências de uso dos provedores antes da publicação

O conteúdo de “Sobre” foi concluído; estes pontos dependem do fluxo de busca e da distribuição do produto, e **não são resolvidos por uma página de créditos**:

1. **Google Books:** a busca atual mistura, reordena e complementa resultados entre provedores. As regras publicadas pedem resultados Google identificados com a marca oficial, links por livro e preservação da ordem, sem mistura com terceiros. É necessário separar a apresentação e o tratamento dos resultados Google ou retirar essa fonte. Isso altera o fluxo aprovado de busca; não foi feito silenciosamente nesta tarefa de créditos.
2. **Google Books e cobrança:** os termos específicos publicados vedam cobrar pelo uso do app sem acordo separado ou permissão escrita. Definir isso antes de disponibilizar uma versão paga; usar uma chave do usuário não demonstra uma dispensa.
3. **BnF:** o ARK já está no resultado, mas a data de recuperação não é preservada. A atribuição precisa acompanhar dados aplicados, sincronizados e exportados, mesmo se o usuário desmarcar identificadores. Isso exige implementar e testar a proveniência, além da referência geral agora incluída no “Sobre”.
4. **Contato público:** a caixa `foliore@johankrs.com`, o domínio e o atendimento de pedidos de remoção ainda dependem da publicação/verificação prevista no plano de lançamento. Não apresentamos os serviços como parceiros ou patrocinadores.

Esses pontos foram identificados nas páginas oficiais consultadas, não constituem uma conclusão de conformidade global do produto.
