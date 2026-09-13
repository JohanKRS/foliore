# Monetização do Foliore

## Oferta

- Download gratuito, todos os recursos disponíveis, até 50 livros presentes no catálogo.
- Produto Google Play **não consumível**: `foliore_unlimited`.
- Uma única opção de compra, ID **`buy`**, tipo comprar (não alugar), sem pré-venda.
- Brasil: **R$ 24,99**, pagamento único. Demais regiões: conversão do Play Console, com arredondamentos comerciais revisados. Exemplos configurados: EUA **US$ 4,99**, Portugal **€ 4,99**, Reino Unido **£ 4,49** e Japão **¥ 820**. Preços podem variar entre países da mesma moeda conforme tributos e ajustes regionais.
- O app usa o preço e o token da oferta retornados pelo Google Play; não calcula câmbio nem fixa texto de preço no APK.
- Nome pt-BR: **Foliore ilimitado**. Descrição: **Remova o limite de 50 livros do catálogo com uma compra única. Sem assinatura.**
- Nome en-US: **Foliore unlimited**. Descrição: **Remove the 50-book catalog limit with a one-time purchase. No subscription.**
- Compra acessível por um botão discreto nas configurações; nenhum anúncio, banner ou oferta automática. O diálogo do limite aponta para Configurações.

## Dados e casos de limite

A política fica no importador, não no repositório geral nem nos motores de sincronização. Importações concorrentes são serializadas na verificação e gravação. A contagem vem do banco de dados, não do filtro visível da biblioteca.

Duplicatas exatas não contam novamente. Uma substituição revisada pode exceder temporariamente a contagem para primeiro salvar a nova cópia com segurança; a antiga só é removida após sucesso. Falha na substituição preserva a antiga. Excluir livros libera vagas. O lote avisa sobre a capacidade antes da importação e informa quantos arquivos não entraram. Arquivos de origem não são alterados.

Restauração, sincronização, edição e exportação continuam disponíveis acima de 50, inclusive após revogação ou reembolso. Nenhum livro é apagado para impor o limite. A compra não é sincronizada pelo Drive: depende da conta da Play Store.

## Compra e verificação

`PlayBillingRepository` usa uma conexão por processo, recibos assinados com RSA, chave pública específica do app, produto/pacote/estado/token/quantidade verificados. Recibos pendentes não liberam acesso. A compra não é consumida. A confirmação (acknowledgement) ocorre depois de gravar o recibo e liberar o acesso, com repetição por WorkManager caso falhe. O retorno ao app consulta compras novamente e recupera transações não recebidas pelo callback.

O cache contém o recibo assinado, não um booleano editável, e fica em `noBackupFilesDir`; é revalidado ao abrir. Consultas incompletas, erros ou ausência de rede não revogam acesso já validado. Uma consulta bem-sucedida sem a compra e com rede validada pode restabelecer o limite gratuito. Nenhum prazo offline arbitrário foi imposto.

A verificação é local, sem backend próprio ou credenciais de conta de serviço no APK. Ela não oferece a mesma resistência à adulteração/replay de um dispositivo comprometido que a verificação por servidor. Revogações offline só são percebidas numa consulta posterior. O Google recomenda backend e notificações em tempo real para maior proteção e confirmação mesmo sem o usuário reabrir o app. Não alegar validação por servidor. A política de privacidade descreve o recibo local e os diagnósticos do SDK.

## Console e builds

Conta: JohanKRS. App criado como rascunho, pacote `com.foliore.android`, idioma pt-BR, download gratuito. Conta do comerciante configurada pelo responsável. Produto **`foliore_unlimited`** criado e opção de compra **`buy`** ativada em **173 países/regiões**, com nome e descrição nos oito idiomas do app. O cadastro do produto ativo não publica o aplicativo. Chave pública copiada do licenciamento do app para o arquivo local ignorado `billing.properties` (modelo em `billing.properties.example`). Ela não é a chave privada de assinatura.

O primeiro bundle assinado, versão **0.1.0 (1)**, foi recebido pelo Console em 12/09/2026 e salvo como **rascunho de teste interno**, com notas da versão. Nenhuma versão foi publicada. O upload liberou o cadastro do produto ao comprovar a permissão `com.android.vending.BILLING`. Para gerar esse bundle de **teste interno**:

```sh
./gradlew :androidApp:bundleRelease -PinternalPlayTest=true
```

Esse parâmetro permite o primeiro upload interno sem marcar falsamente a publicação ou compra como verificada. Não contorna a assinatura nem a verificação da chave pública. Não publicar esse artefato em produção antes de terminar as verificações de publicação.

Com o produto ativo, usar contas de testadores de licença do Console e validar com os meios de pagamento de teste. Testadores de licença podem instalar o APK por ADB com o mesmo nome de pacote; para validar a distribuição completa, também instalar pela faixa de teste. A própria conta publicadora já é considerada testadora de licença pelo Google, mas a conta selecionada no fluxo de compra precisa ser conferida. Não fazer cobranças reais para simular a compra. Testar sucesso, cancelamento, pagamento pendente aprovado/recusado, rede interrompida após pagamento, restauração em outro aparelho, reinstalação e reembolso/revogação. Só marcar `billingProductVerified=true` em `publication.properties` depois desses testes reais do Billing. O teste local de assinaturas não substitui essa etapa.

Para produção, `bundleRelease` sem o parâmetro exige chave de Billing, produto testado e os requisitos públicos já documentados (privacidade, identidade e suporte). O cadastro do produto não substitui concluir a ficha e as divulgações de dados da Play Store.

## Verificação em 12/09/2026

- `assembleRelease`, `bundleRelease -PinternalPlayTest=true`, `testDebugUnitTest` e `lintRelease`: concluídos; 113 testes sem falhas.
- Harness isolado com EPUBs reais: limite de 50, rejeição do 51º, duplicatas, substituição segura, vaga após exclusão, importações concorrentes e modo ilimitado verificados. Restauração de 55 livros pelo motor de sincronização preservou todos os hashes dos originais; edição e preparação continuaram disponíveis acima do limite.
- Oferta e condições inspecionadas em celular claro e tablet escuro no emulador. Compatibilidade futura aparece somente nas condições.
- Console: bundle aceito, rascunho de teste interno salvo, produto `foliore_unlimited` / opção `buy` ativos. Preços salvos após conferir 93 expectativas regionais. R$ 24,99 foi mantido por orientação do responsável; os demais finais comerciais preservam a proximidade da conversão inicial.
- Revisão do rascunho: três avisos, sem erro bloqueante — ausência de testadores na faixa, arquivo de desofuscação e símbolos de depuração nativos. Nenhuma versão publicada.
- A proteção de produção foi testada: `validatePlayRelease` recusa a versão enquanto `billingProductVerified` não estiver confirmado.
- **Pendente:** compra, cancelamento, pagamento pendente, acknowledgement, restauração e revogação com a Play Store. Celular e tablet desconectaram do ADB antes da atualização; nenhum deles recebeu o APK de monetização nesta etapa. Não marcar esses cenários como aprovados nem habilitar `billingProductVerified` apenas porque o produto está ativo.

## Condições de uso e compra

As condições estão traduzidas nos oito idiomas do app, acessíveis por link no diálogo de compra e em Sobre. O aviso hipotético sobre futuros Androids não aparece destacado na oferta, conforme orientação do responsável. Usar **compra única**, sem promessa comercial de suporte eterno. O desbloqueio não tem expiração programada.

O primeiro trecho, “Arquivos e backup”, se aplica à biblioteca gratuita e ilimitada: orienta manter cópias independentes fora do Foliore, explica que a sincronização propaga exclusões e que a recuperação depende dos arquivos e versões disponíveis. A política de privacidade usa a mesma distinção entre sincronização e backup independente. Não há isenção geral por perda de dados, transferência integral do risco ao usuário ou renúncia a direitos; o texto preserva as responsabilidades legais, conforme os arts. 25 e 51 do CDC. Não foi acrescentado um alerta permanente nem uma etapa de aceite na interface.

A cláusula de compatibilidade não elimina atualizações necessárias à conformidade, garantias ou reparações obrigatórias. Não equivale a uma dispensa universal de manutenção nem a um prazo legal fechado. Na UE, a Diretiva 2019/770 contempla atualizações durante o período razoavelmente esperado inclusive no fornecimento único; no Brasil, oferta, informação adequada e direitos do consumidor continuam vinculantes. Antes de distribuição internacional, revisar o texto para os mercados e a identidade comercial efetivos. Manter as mesmas condições na página pública de suporte quando ela for publicada.

Fontes primárias consultadas em 12/09/2026:
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
- https://developer.android.com/google/play/billing/one-time-products
- https://developer.android.com/google/play/billing/test
- https://support.google.com/googleplay/android-developer/answer/6062777
- https://support.google.com/googleplay/android-developer/answer/6334373
- https://www.planalto.gov.br/ccivil_03/leis/l8078compilado.htm (arts. 6, 24, 25, 30, 46 e 51)
- https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32019L0770 (arts. 8 e 22)

Referência de apresentação, sem copiar termos: Smart Launcher expõe limitações futuras nos termos da licença; Poweramp destaca compra única na oferta. Essas práticas não substituem os direitos aplicáveis ao Foliore.
- https://www.smartlauncher.net/terms-and-conditions-new
- https://powerampapp.com/
