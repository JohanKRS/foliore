# Preparação do lançamento — 13/09/2026

## Página da Play Store

- Página padrão em português do Brasil e sete traduções redigidas e cadastradas manualmente: inglês (EUA), espanhol (Espanha), francês (França), alemão, italiano, japonês e chinês simplificado.
- Descrições e textos alternativos preparados em `artwork/play-store/store-listings.json`. Os textos alternativos estão no arquivo; o novo editor de recursos não expôs um campo para cadastrá-los.
- Ícone aprovado e gráfico 1024 × 500 carregados; 48 screenshots reais, seis por idioma, carregadas e ordenadas 01–06.
- Cada biblioteca tem seis livros demonstrativos. O tema claro usa a ordem `[0,1,2,3,4,5]`; o escuro usa `[4,5,0,1,2,3]`. Detalhes e metadados usam a obra 0 no claro e a obra 4 no escuro.
- Capturas PNG RGB 1080 × 1920; grade nativa de duas colunas. Não houve montagem da interface nem alteração do layout do aplicativo para as capturas.
- Fontes, traduções e créditos dos EPUBs documentados em `artwork/play-store/LOCALIZED-SOURCES.md` e `PROVENANCE.md`. Os livros não são distribuídos no aplicativo nem no site; a descrição da loja explicita isso.
- Manifestos de captura, hashes e APK usado em `artwork/play-store/screenshots/`. Cópias com prefixo do idioma e manifesto em `upload-assets/` e `upload-manifest.json`.
- Categoria **Livros e referências** salva. Página exibiu **Pronta para revisão** após o salvamento. Nenhuma revisão ou publicação foi iniciada.
- Declaração de arte com IA salva e conferida após reabrir: ícone, gráfico e screenshots 01/02/03/05 dos oito idiomas (34 recursos). As screenshots 04/06 mostram somente o editor nativo e seus campos, sem as capas geradas.

## Declarações já salvas no Console

Sem anúncios; sem função governamental; sem recursos financeiros; sem recursos de saúde; sem uso de Advertising ID. Isso não substitui a declaração detalhada de Segurança dos dados, preparada separadamente em `play-store-data-safety-2026-09-13.md`.

## Build e site

- Release `versionCode=2`, `versionName=0.1.0`, assinado e compilado com `-PinternalPlayTest=true` para preparar testes internos.
- AAB: `androidApp/build/outputs/bundle/release/androidApp-release.aab`.
- Bundle 2 carregado no rascunho de teste interno **0.1.0 (2) — Preparação do lançamento**; bundle 1 retirado somente desta versão e preservado na biblioteca de artefatos. Notas da versão cadastradas nos oito idiomas, com confirmação de **Alterações salvas**. A distribuição interna permanece inativa.
- SHA-256 AAB: `ab9f6aade7a77fa1e6fa09c130fd4967d680bd70334c68eb925a4f9387be3bc6`.
- SHA-256 APK: `5278b9290e764c9adfaaec49b27c8afb0f5a1f482456a8f9eca049f01417a357`.
- A validação do teste interno mostrou **zero erros e três avisos**: ainda não há testadores; ausência de mapping de desofuscação; ausência de símbolos nativos. O release atual não ativa minificação/R8 e não gera mapping próprio; os únicos binários nativos são `libandroidx.graphics.path.so` de dependência AndroidX. Não foram inventados arquivos de símbolos. O botão **Salvar e publicar** não foi acionado.
- APK: `androidApp/build/outputs/apk/release/androidApp-release.apk`; cópia em `/home/johan/Downloads/Foliore-0.1.0-build2-2026-09-13.apk`.
- Nenhum EPUB demonstrativo está embutido no AAB ou APK.
- Site estático separado em `/home/johan/Projetos/foliore-site/dist`, com README de instalação e pacote `/home/johan/Downloads/Foliore-site-2026-09-13.zip`.
- HTML/CSS/JS e recursos locais, sem etapa de build. Prévia local em `http://127.0.0.1:8766/`.

## Pendências reais antes de publicar

1. Colocar `foliore.johankrs.com` em HTTPS e ativar/verificar `foliore@johankrs.com`; cadastrar URLs e contato somente quando operacionais.
2. Hospedar e testar o endpoint de relatos de IA. A pasta estática do site não implementa `/api/content-reports`; o backend existente está em `support/server.mjs`.
3. Definir público-alvo. A pergunta sobre adaptações para adolescentes foi respondida, mas o usuário ainda não escolheu a faixa. Não foi declarada restrição 18+ por conta própria.
4. Concluir classificação indicativa, política de privacidade, Segurança dos dados e instruções de acesso para revisão com informações verificáveis.
5. Preparar acesso de revisão para integrações opcionais e compras. Validar OAuth externo/assinatura Google Play e compras, restauração e reembolso pelo Play.
6. A conta mostrou exigência de teste fechado com pelo menos 12 participantes por 14 dias contínuos; o fluxo ainda não foi iniciado.
7. As verificações de publicação pública continuam bloqueando um release de produção enquanto esses requisitos não estiverem comprovados. O bypass de teste interno não significa que estão concluídos.

Não houve commit nem push nesta etapa.
