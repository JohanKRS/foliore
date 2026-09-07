# Foliore — revisão antes da Play Store

> **Atualização após as correções:** consulte [FIXES.md](/home/johan/Projetos/foliore/docs/audits/2026-09-07/FIXES.md) para o estado atual, testes e pendências de lançamento. O conteúdo abaixo registra a auditoria original, antes das correções autorizadas.

Revisão de 7 de setembro de 2026. Base: `cbc480d3928222065b3741ca0692ffd5c4c68349`. Android/Compose, módulo compartilhado, configuração de release e experiência em Pixel 8 / API 36. As correções locais da barra inferior descritas abaixo foram feitas depois da revisão de segurança da base.

**Eu ainda não publicaria a versão atual.** A prioridade é impedir perda de livros na sincronização, corrigir o transporte SMTP e a validação de entradas externas, e fechar os requisitos de distribuição. A importação normal e a apresentação dos livros funcionam, mas os testes existentes não cobrem os principais cenários de falha encontrados.

## O que foi executado

- Leitura estática de todo o código Kotlin (103 arquivos na base), configuração Android, armazenamento, parser, sincronização, serviços externos, telas e testes. Recursos binários e arquivos gerados não foram auditados; traduções foram verificadas estruturalmente.
- Compilação de APK debug e AAB release, lint release e 35 testes existentes: **27 Android + 8 compartilhados, sem falhas**. Lint sem erros, com avisos que precisam de triagem; o alerta de idiomas no App Bundle é relevante.
- Biblioteca com **15 EPUBs reais**, copiados da pasta autorizada no Drive local. EPUB 2 e 3, capas claras/escuras/coloridas, títulos longos e um arquivo de aproximadamente 20,5 MB. Os originais do Drive não foram alterados.
- Importação pelo seletor Android no início; carga adicional por receptor exclusivo de debug. Teste de importação recebida por `ACTION_VIEW`, duplicata exata e faixa de progresso usando um fluxo local temporariamente lento, com bytes de um EPUB válido. Esse fluxo foi apenas uma ferramenta de QA, não entrou no app.
- Navegação, editor, configuração de idioma, temas claro/escuro, conteúdo rolando atrás da barra, estados ativos/desabilitados e faixa de progresso visível/oculta.
- Exportação real de um livro com rascunho não salvo: a tela mostrava `Dust_`, mas o arquivo exportado continuou com `<dc:title>Dust</dc:title>`. O seletor de compartilhamento foi aberto; nenhum arquivo foi enviado a terceiros.
- Inspeção do AAB: target SDK 36; biblioteca nativa incluída com alinhamento ELF de 16 KB. Assinatura de upload ausente no artefato gerado neste ambiente.

Os caminhos de ataque foram validados por leitura independente de fonte, sem exploração em dispositivo, interceptação SMTP ou execução de provedor malicioso. Drive autenticado, SMTP/Kindle e geração paga por IA não receberam teste de ponta a ponta. Não houve acesso ao Play Console, execução em telefone físico ou certificação de acessibilidade/performance. O emulador inicialmente caiu no processo gráfico do host com renderização por software; as capturas desse estado foram descartadas e o teste prosseguiu com GPU do host. Isso não foi classificado como crash do app.

## Corrigir antes de publicar

As prioridades desta seção são de lançamento, não uma reclassificação da severidade técnica dos achados de segurança.

### 1. Sincronização pode apagar a única cópia de livros

**P1 — análise estática confirmada; não executar este cenário na biblioteca real.**

Ao falhar o download de um livro que existe apenas no remoto, a sincronização continua. A lista de pastas permitidas é montada a partir dos livros disponíveis localmente. Na primeira sincronização, a atualização da estrutura permite a limpeza de pastas remotas fora dessa lista. O manifesto mesclado pode continuar referenciando o livro cujo diretório foi apagado.

Fonte: [download e continuação após erro](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:150), [limpeza remota](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:278).

**Correção:** excluir somente por tombstone explícito, preservar todos os livros retidos no manifesto e suspender limpeza quando qualquer download estiver incompleto. **Aceite:** dispositivo vazio + um download remoto com falha deve deixar todos os bytes remotos intactos, inclusive após nova tentativa.

Há mais três problemas associados:

| Problema | Consequência | Correção e teste de aceite |
|---|---|---|
| [Escolha heurística de uma raiz e remoção das outras](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:123) | Duas bibliotecas diferentes podem ser tratadas como duplicatas e uma ser apagada. | Identificar bibliotecas por identidade; preservar/quarentenar ou mesclar antes de limpar. Testar raízes com livros distintos. |
| [Pastas contadas diferenciando maiúsculas/minúsculas](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1299), mas provedor encontra nomes ignorando caixa | `Author - Book` e `Author - book` podem apontar para o mesmo diretório e sobrescrever conteúdo. | Incluir ID estável nos nomes e normalizar consistentemente. Testar colisões em provedor que ignora caixa. |
| [Bloqueio restrito à instância do motor](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:45) e trabalhos independentes | Sincronização manual, periódica e exclusão podem operar simultaneamente; caches e manifesto não têm coordenação global suficiente. | Coordenador único no processo, exclusão mútua por biblioteca e detecção de versão/conflito remoto. Testar operações sobrepostas em dois dispositivos. |

### 2. Quatro vulnerabilidades de segurança

**Quatro achados de severidade média, confiança alta na análise de fonte.** O relatório canônico foi finalizado pelo Codex Security e está em [security/report.md](/home/johan/Projetos/foliore/docs/audits/2026-09-07/security/report.md). Ele contém caminhos de ataque, pré-condições, contraprovas e remediações detalhadas.

| Achado | Pré-condição / impacto | Correção |
|---|---|---|
| [ID remoto usado como caminho local](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:522) | Quem puder alterar a pasta de sync/provedor pode fornecer `bookId` com `../` e atingir outros diretórios privados do app. A sandbox limita o alcance, mas preferências e biblioteca podem ser substituídas. | Validar UUID canônico antes de planejar operações; verificar contenção do caminho final; staging/backup com nomes aleatórios independentes do ID externo. |
| [TLS SMTP sem verificação de hostname](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:145) | Um interceptador com certificado confiável para outro domínio pode se passar pelo servidor. A cadeia é validada, o nome do servidor não. | Ativar identificação do endpoint em STARTTLS e TLS implícito, ou usar biblioteca adequada. Testar certificado de domínio incorreto nas duas modalidades. |
| [AUTH sem TLS em porta personalizada](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:30) | Fora de 465/587, ausência de STARTTLS permite credenciais recuperáveis em texto claro. O padrão 587 falha de forma segura. | Exigir TLS antes de AUTH em qualquer porta. O teste atual que aceita SMTP sem TLS precisa ser substituído por teste de recusa. |
| [Importação/descompressão/decodificação sem limites suficientes](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15) | EPUB comprimido malicioso, imagem enorme ou fluxo sem fim pode consumir memória/disco; compartilhamento de capa também faz cópia síncrona na Activity. | Limites de bytes, expansão total/por entrada, quantidade de entradas e pixels antes de alocar. I/O fora da thread principal, cancelamento e limpeza de temporários. |

Há boas bases: credenciais protegidas com AES-GCM e Android Keystore, consultas SQL parametrizadas, FileProvider não exportado e restrito a caminhos específicos, concessão de leitura por URI, escopo `drive.appdata` e receptores de teste excluídos do release. Esses controles não eliminam os quatro problemas acima.

### 3. Exportação precisa corresponder ao que o usuário vê

**P1/P2 — divergência de rascunho reproduzida em execução; demais casos validados em fonte.**

- **Exportar/Kindle ignoram alterações não salvas.** Apenas Salvar envia o rascunho atual; Exportar e Kindle usam o registro persistido. Reproduzido com `Dust_` na tela e `Dust` no OPF exportado. Ao sair também falta proteção para alterações pendentes. [Callbacks](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/ui/details/EditMetadataScreen.kt:215). Salvar automaticamente antes da ação ou oferecer uma decisão explícita sobre o rascunho.
- **`$` e `\` em metadados entram como sintaxe de substituição de regex.** Escapar XML não escapa a string de replacement; um título com `$5` pode falhar e barras podem ser consumidas. Usar substituição literal segura. [Preparação do OPF](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:249). Testar títulos, autores e descrições com esses caracteres.
- **Capa convertida para JPEG pode manter extensão/media-type PNG ou SVG do EPUB original.** A escrita substitui bytes da entrada existente; o manifesto é atualizado somente ao adicionar uma entrada ausente. [Substituição](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:156). Renomear/atualizar referências e MIME de modo consistente. Validar EPUB 2/3, PNG/SVG/JPEG e leitores reais, além de EPUBCheck.

### 4. Acessibilidade não pode depender de exploração por toque

**P1 — fonte; validação com serviços assistivos ainda necessária.** [LibraryScreen](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/ui/library/LibraryScreen.kt:858) expõe semântica conforme `isTouchExplorationEnabled`. Com um serviço de acessibilidade ativo sem esse modo, o host Compose pode receber `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`. Isso pode esconder a interface de Switch Access/Voice Access. Manter a árvore semântica disponível e testar esses serviços, TalkBack, seleção anunciada, foco, alvos de toque e fonte ampliada.

### 5. Fechar configuração e obrigações da Play Store

| Item | Situação encontrada / próximo passo |
|---|---|
| Política de privacidade e Data Safety | Não encontrei rota/link de política dentro do app. Criar política pública acessível e entrada no app; preencher Data Safety conforme os fluxos reais de Drive, SMTP, buscas e OpenAI. O Console não foi inspecionado. [Política oficial](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). |
| Conteúdo gerado por IA | A geração de capas por texto merece tratamento explícito da política: prevenção de conteúdo proibido e mecanismo de denúncia no app. Não encontrei denúncia. Confirmar o enquadramento de cada função; geração de imagens está no escopo, enquanto algumas funções limitadas de produtividade têm exceções. [Política](https://support.google.com/googleplay/android-developer/answer/13985936?hl=en), [escopo](https://support.google.com/googleplay/android-developer/answer/14094294?hl=en). |
| Idiomas via App Bundle | Lint aponta `AppBundleLocaleChanges`: o app troca locale por contexto customizado, mas o AAB mantém divisão padrão por idioma. O APK universal funcionar não garante recursos instalados via Play. [Código](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/storage/AppLanguagePreferenceRepository.kt:54). Usar estratégia compatível de idiomas e entrega, ou desabilitar a divisão por idioma. Testar instalação derivada do AAB. [Documentação](https://developer.android.com/guide/app-bundle/configure-base#disable_config_apks). |
| Assinatura | `bundleRelease` funciona sem `keystore.properties`, mas o AAB deste ambiente ficou sem assinatura de upload. Configurar chave de upload, Play App Signing e credenciais OAuth com o certificado de distribuição correto. [Gradle](/home/johan/Projetos/foliore/androidApp/build.gradle.kts:9). |
| Target API | `targetSdk = 36`, adequado à exigência de API 36 a partir de 31/08/2026. Não tratar o aviso genérico de target do lint como motivo isolado para mudar para API 37. [Requisito oficial](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |
| Páginas de memória de 16 KB | O alinhamento ELF da biblioteca nativa incluída é 16 KB. Ainda testar o bundle instalado em ambiente de 16 KB; inspeção ELF não equivale a compatibilidade completa em execução. [Guia](https://developer.android.com/guide/practices/page-sizes). |
| Teste fechado | Se for conta pessoal criada após 13/11/2023, verificar exigência de 12 participantes por 14 dias contínuos para acesso à produção. Não presumo o tipo/data da conta. [Regra oficial](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB). |

## UI/UX: barra de vidro e estados

**Corrigido localmente nesta revisão:** barra inferior com fundo da captura do Haze definido antes do desfoque, blur uniforme de 28 dp e camada de contraste constante de 80%. Não há mais intensidade zero sobre a primeira fileira de controles nem gradiente recalculado sobre a altura total variável. A primeira tentativa com maior opacidade foi rejeitada pelo usuário e substituída por esta versão mais transparente.

Definir o fundo do blur foi essencial: regiões transparentes da captura deixavam o conteúdo original nítido aparecer por baixo. A versão final mantém variações amplas de cor e desfoca os caracteres. Botões de ação mantêm seu próprio fundo; os demais controles usam a superfície de vidro.

- Selecionado: dourado e rótulo em negrito; não selecionado: cinza de contraste maior.
- Ação disponível: dourado e borda de destaque; indisponível: cinza legível e borda discreta. Salvar com alterações usa preenchimento de destaque.
- A faixa de progresso recebe as mesmas cores acessíveis e não altera o contraste das fileiras abaixo.
- Corrigido também um defeito encontrado ao alternar o tema: a publicação das ações podia capturar um ID de rota ainda desatualizado e deixar Exportar/Kindle indisponíveis. Agora o ID vem diretamente da entrada do editor. Verificado em escuro → claro → escuro: Salvar desabilitado sem alterações; Exportar e Kindle habilitados nas três etapas.

Arquivos: [barra e associação das ações](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/navigation/FolioreNavHost.kt:493), [cores](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/ui/theme/Color.kt:10), [texto de progresso](/home/johan/Projetos/foliore/androidApp/src/main/kotlin/com/foliore/android/ui/library/SyncProgressStrip.kt:76).

O contraste foi calculado em sRGB a partir das cores e do piso de opacidade, considerando fundo preto no tema claro e branco no escuro, sem contar o benefício do blur. Os rótulos ativos e secundários excedem 4,5:1 nessas condições; controles desabilitados conservam texto legível. É um critério de projeto, não certificação em todos os dispositivos. Referência: [WCAG 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

### Jornada e saúde dos fluxos

1. **Importar e formar biblioteca — bom no uso normal.** Os 15 livros apareceram com capas e metadados. Melhorar limites/erros de entrada, cancelamento e teste com biblioteca grande. A duplicata exata do teste não acrescentou outro exemplar.
2. **Percorrer biblioteca — legibilidade inferior corrigida; revisão geral ainda necessária.** Claro/escuro, capas contrastantes e títulos atrás da barra foram capturados. O cabeçalho superior ainda pode deixar conteúdo competir com seu texto e merece aplicar o mesmo princípio de proteção. Acessibilidade continua pendente.
3. **Abrir editor — identidade visual consistente, densidade ruim em telefone.** A capa ocupa quase toda a primeira tela e os campos ficam muito abaixo. Reduzir a capa inicial, trazer título/autor e ação principal para perto do topo, e separar detalhes de edição se isso fizer sentido no produto. O clique no livro abre “Edit book”, não uma página de leitura.
4. **Editar/exportar — atenção.** O rascunho não salvo e o arquivo exportado divergem; a correção de estado após troca de tema está feita. A mensagem “Sync folder connected” também aparece sem refletir conexão real e deve usar o estado do provedor.
5. **Acompanhar operação — legibilidade corrigida.** Faixa visível/oculta testada nos dois temas, biblioteca/editor; botões indisponíveis continuam identificáveis. Em tarefas longas, melhorar cancelamento e mensagens de falha/recuperação.
6. **Configurar integrações — revisão necessária.** Explicar o que será enviado, distinguir configuração de conexão efetivamente validada e explicitar uso/custos da chave própria de IA. Remover `originalFilePath` do prompt da IA se não houver necessidade funcional. Nenhuma chamada paga foi feita nesta auditoria.

### Evidência visual

Biblioteca: claro/escuro, sem/com faixa de progresso. São recortes de capturas reais, sem reconstrução visual.

![Matriz da barra inferior na biblioteca](/home/johan/Projetos/foliore/docs/audits/2026-09-07/screenshots/library-glass-matrix.png)

Editor: texto de descrição atrás da barra, com operação em andamento. Todos os botões de ação estão corretamente indisponíveis durante a importação.

![Editor com progresso nos dois temas](/home/johan/Projetos/foliore/docs/audits/2026-09-07/screenshots/editor-progress-compare.png)

Estados disponíveis/indisponíveis após a correção da associação das ações (livros diferentes entre as linhas, conforme as capturas):

![Matriz dos estados no editor](/home/johan/Projetos/foliore/docs/audits/2026-09-07/screenshots/editor-glass-matrix.png)

As capturas completas estão em `screenshots/`. “Barra de status” nesta revisão significa a **faixa de progresso do Foliore**, conforme esclarecimento do usuário. A barra Android de relógio/bateria não faz parte da matriz.

## Melhorias seguintes

- Backup/restauração: há backup Android habilitado sem regras específicas. Preferências criptografadas podem ser restauradas sem a chave correspondente do Keystore; a interface pode indicar chave configurada apesar de a decodificação falhar. Definir exclusões/regras e recuperação. Não usar backup automático como garantia de preservação de uma biblioteca grande.
- Performance: medir importação, rolagem e memória em telefone físico intermediário, biblioteca grande e Android antigo. A dependência Haze é `2.0.0-alpha03`; registrar regressões e validar antes de adotar novas versões. Não concluir desempenho de produção a partir deste emulador.
- Tamanho/release: avaliar minificação e reo moçãde recursos com testes de release; hoje estão desativadas. Não ativar às cegas em biblioteca com serialização/reflexão.
- Atualizar README e especificação de sync: há documentação dizendo que sincronização não foi implementada, divergindo do código.
- Ampliar a matriz: Android 8/11 (fallback sem blur padrão), API 36 com navegação por botões/gestos, fonte ampliada, tablet e rotação. As telas de tablet foram lidas, mas não receberam QA visual em dispositivo nesta rodada.

## Ordem sugerida de trabalho

1. Preservação de dados na sincronização, caminhos externos e TLS obrigatório/correto.
2. Limites de importação e consistência do EPUB exportado.
3. Acessibilidade, rascunho/saída do editor, privacidade e requisitos de IA.
4. Assinatura, idiomas no AAB e teste fechado/internal track.
5. Performance em aparelhos reais, ajuste de densidade do editor e acabamento do cabeçalho.

Somente a barra inferior e a associação das ações após restauração de tema foram corrigidas nesta rodada. Os demais itens são um backlog de revisão, com distinção entre evidência estática, reprodução real e validação ainda pendente. Nenhum upload/publicação foi realizado.
