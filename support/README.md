# Canal de denúncia de conteúdo

O app envia texto, sem EPUB, capa, credenciais ou anexos, para `https://foliore.johankrs.com/api/content-reports`. O endereço ainda precisa ser publicado e verificado; o aplicativo não confirma recebimento quando a requisição falha.

## Operação

1. Execute `node support/server.mjs` com Node.js 22 ou superior sob um supervisor de processos, usando um usuário de sistema exclusivo. A escuta é somente em `127.0.0.1:8787`; `FOLIORE_REPORTS_PORT` permite alterar a porta.
2. Defina `FOLIORE_REPORTS_DIR` como um diretório persistente e privado, fora da raiz pública do site. O padrão é `support/private-reports`. Diretório e arquivos usam permissões 0700/0600. Restrinja também os backups e acessos administrativos.
3. Configure um proxy HTTPS no domínio para encaminhar somente `/api/content-reports`. O proxy deve **sobrescrever** `X-Real-IP` com o IP da conexão, limitar o corpo a 32 KB e impor limite global de requisições/conexões. Não exponha a porta interna.
4. Configure quota e alertas de espaço em disco, disponibilidade e respostas 5xx. O limite por IP na aplicação é temporário e por processo; não substitui os controles do proxy. Nenhuma denúncia pode ser descartada para liberar espaço antes do prazo definido.
5. Designe um responsável para revisar os arquivos recebidos diariamente, avaliar o conteúdo e corrigir/moderar a funcionalidade quando necessário. O serviço não envia e-mails nem realiza moderação automática. Registre a resolução em um sistema privado com acesso restrito.
6. Os arquivos recebidos são removidos após 90 dias, na inicialização e na rotina horária. Aplique o mesmo prazo aos backups. Evite registrar corpos das requisições no proxy.
7. Publique a política em `/privacy/`, revise a identidade pública e valide a caixa `foliore@johankrs.com`. Faça um envio sintético pelo app em produção, confira o arquivo privado e remova esse registro de teste. Só então marque as confirmações em `publication.properties`.

O servidor retorna 201 somente após gravar e sincronizar arquivo e diretório em disco. Erros de JSON retornam 400, formato incorreto 415, excesso de requisições 429. O app mantém o texto em caso de erro para nova tentativa.

Teste local, sem envio externo: `node support/server.test.mjs`.
