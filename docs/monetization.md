# Distribuição do Foliore: gratuito e open source

Decisão de 21/11/2026. O Foliore é gratuito e de código aberto, sob a GPL-3.0 (`LICENSE` na raiz). Não há compras, assinaturas, anúncios nem limite de livros. Nenhum login é necessário para usar o app: Google Drive, Gmail, Kindle, ChatGPT e a chave da API da OpenAI são opcionais.

## O que foi removido

- O produto não consumível `foliore_unlimited` (opção `buy`), o limite de 50 livros no importador, os lembretes a partir de 45 livros, o diálogo de capacidade e as condições de compra.
- A dependência `com.android.billingclient:billing`, a permissão `com.android.vending.BILLING` que ela trazia, a chave pública em `billing.properties` e a verificação local de recibos.
- O item `billingProductVerified` de `publication.properties` e a checagem correspondente em `validatePlayRelease`.

Livros importados acima de 50 em versões anteriores continuam onde estão. Recibos antigos ficam no armazenamento privado sem uso e são removidos com os dados do app; nenhuma migração é necessária.

## Recursos de IA

Os recursos de IA continuam opcionais e funcionam de duas formas:

- **Entrar com o ChatGPT** (programa Sign in with ChatGPT da OpenAI para projetos open source): preenche sinopse e detalhes e procura capas existentes na web, usando o plano do usuário (Plus ou Pro). Não há custo para o projeto.
- **Chave da API da OpenAI**: gera capas novas e também preenche detalhes quando o ChatGPT não está conectado. O uso é cobrado pela OpenAI na conta da chave.

A geração de imagens ainda não está disponível pelo login do ChatGPT. O caminho já está no código, desligado em `AiFeatureFlags.CHATGPT_IMAGE_GENERATION`; quando a OpenAI liberar, basta ativar a flag e testar.

## Play Console

O app continua no Google Play como download gratuito. Antes de publicar esta versão:

1. Desativar o produto `foliore_unlimited` em **Monetizar > Produtos > Produtos no app**. Um app gratuito não pode voltar a ser pago, mas isso não é necessário aqui.
2. Revisar a seção **Segurança dos dados**: o histórico de compras deixa de ser coletado; o login com ChatGPT envia à OpenAI os pedidos de IA feitos pelo usuário (veja `docs/play-store-data-safety-2026-09-13.md`).
3. Declarar o serviço em primeiro plano `shortService`, usado só enquanto o login com ChatGPT está aberto no navegador, em **Conteúdo do app > Serviços em primeiro plano**.
4. Atualizar a descrição da ficha (`artwork/play-store/store-listings.json`) e as notas da versão.
5. Publicar o repositório do GitHub antes de distribuir, porque a GPL-3.0 e o programa Sign in with ChatGPT exigem o código-fonte público.

O bundle de teste interno continua sendo gerado com:

```sh
./gradlew :androidApp:bundleRelease -PinternalPlayTest=true
```

Para produção, `bundleRelease` sem o parâmetro exige os requisitos públicos de `publication.properties` (privacidade, denúncias, identidade e suporte).
