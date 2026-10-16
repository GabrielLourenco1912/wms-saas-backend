# WMS SaaS Backend

API Java 21 / Spring Boot 4.0.6. Implementa autenticação, perfis/permissões, cadastros, pré-recebimento/conferência, putaway, estoque/transferências, ondas/picking, expedição e inventário, com transações, idempotência e histórico imutável.

A organização por módulos, baseada em `inatback/main`, e as orientações para continuar a implementação estão em [docs/ARQUITETURA.md](docs/ARQUITETURA.md). Os módulos operacionais possuem controllers, DTOs, serviços e persistência. A rastreabilidade e os limites de validação estão em [docs/REQUISITOS.md](docs/REQUISITOS.md).

A matriz por RN/RF e os contratos de autorização das rotas estão em [docs/AUTORIZACAO_WMS.md](docs/AUTORIZACAO_WMS.md).

Para construir os clientes web e mobile, consulte o [guia de integração da API](docs/API_CLIENTS.md): catálogo completo de endpoints, payloads, respostas, autenticação, permissões, filtros e exemplos de consumo.

## Executar

Configurar `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e `JWT_SECRET` (ao menos 32 bytes UTF-8). O emissor pode ser definido por `JWT_ISSUER`; o padrão é `https://api.wmssaas.com`.

A autenticação usa códigos enviados por e-mail. Os três fluxos compartilham templates com a identidade WMS SaaS; recursos, prévias locais e compatibilidade estão em [docs/IDENTIDADE_EMAILS.md](docs/IDENTIDADE_EMAILS.md). Configurar `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` e, se necessário, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true` e `MAIL_STARTTLS_ENABLED=true`. O padrão local usa servidor SMTP em `localhost:1025`.

```bash
./mvnw spring-boot:run
```

`CORS_ALLOWED_ORIGINS` aceita uma lista separada por vírgulas; o padrão é `http://localhost:3000`. Para desenvolvimento web em HTTP, usar `REFRESH_COOKIE_SECURE=false`; o padrão é `true`.

## Primeiro gestor

O cadastro de usuários é administrativo (RF016); `/api/auth/register` exige um usuário com permissões de cadastro e atribuição de perfil.

Para provisionar o primeiro gestor, definir `WMS_BOOTSTRAP_ENABLED=true`, `WMS_BOOTSTRAP_EMAIL`, `WMS_BOOTSTRAP_PASSWORD` e, opcionalmente, `WMS_BOOTSTRAP_NAME`. A senha é validada e armazenada com BCrypt. A conta inicia `INVITED` e confirma o e-mail pelo primeiro login. Se o e-mail já pertencer a uma conta legada, essa conta é promovida e sua senha é substituída pela configuração explícita. Se já existir um gestor, o bootstrap não modifica contas.

Desabilitar o bootstrap e remover a senha de provisionamento da configuração após a criação.

## Banco de desenvolvimento

As migrations iniciais foram consolidadas para o desenvolvimento: V1 cria a autenticação completa, os perfis e as permissões; V2 cria as tabelas operacionais do WMS. Bancos novos aplicam ambas automaticamente. Hibernate usa `validate`.

Um banco criado com as versões anteriores dessas migrations não é atualizado por esta baseline de desenvolvimento. Para executar a estrutura atual, provisionar uma base nova e vazia, conservando os dados antigos se ainda forem necessários. Não habilitar `FLYWAY_BASELINE_ON_MIGRATE` para pular a criação das tabelas de autenticação.

Nenhuma base existente foi alterada durante este trabalho; os testes executam bancos próprios. A reorganização dos pacotes Java não altera essas migrations.

O Flyway instala quatro gatilhos MySQL que rejeitam UPDATE/DELETE no livro razão e no histórico. Em servidores com binary logging, executar as migrations com uma conta administrativa adequada, separada da conta da aplicação quando necessário, usando `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER` e `SPRING_FLYWAY_PASSWORD`. Os testes MySQL usam essa separação; a conta da aplicação continua sem privilégios globais de administração.

## Contrato de login

`POST /api/auth/login` passou a retornar 202 com um desafio, sem tokens. O cliente pede o código enviado por e-mail e chama `POST /api/auth/challenge/verify`; essa resposta contém o access token. `X-Client-Type: WEB` usa cookie HttpOnly para refresh, enquanto `MOBILE` retorna refresh no corpo.

O contexto do usuário está em `GET /api/users/me`. Administração usa `/api/users`, `/api/profiles` e `/api/permissions`; detalhes e payloads estão na documentação de autorização.

Os contratos operacionais e exemplos de requisição estão em [docs/OPERACOES.md](docs/OPERACOES.md). A aceitação dos clientes web/mobile, as medições de desempenho e a implantação de produção permanecem como atividades posteriores; ver [rastreabilidade dos requisitos](docs/REQUISITOS.md).

## Cadastros disponíveis

As coleções abaixo oferecem listagem paginada por `GET`, criação por `POST`, consulta por `GET /{id}` e edição por `PUT /{id}`:

| Recurso | Coleção | Permissões de consulta/criação/edição |
| --- | --- | --- |
| Armazéns | `/api/warehouses` | `WAREHOUSE_VIEW/CREATE/EDIT` |
| Zonas | `/api/zones` | `WAREHOUSE_VIEW/CREATE/EDIT` |
| Posições | `/api/positions` | `WAREHOUSE_VIEW/CREATE/EDIT` |
| Categorias | `/api/categories` | `PRODUCT_VIEW/CREATE/EDIT` |
| Produtos | `/api/products` | `PRODUCT_VIEW/CREATE/EDIT` |
| Clientes e fornecedores | `/api/partners` | `ORDER_VIEW/CREATE/EDIT` |

Embalagens usam `GET/POST /api/products/{productId}/packages` e `PUT /api/products/{productId}/packages/{packageId}`, com permissões de produto. Todas as coleções, incluindo embalagens, perfis e permissões, usam paginação com `page=0` e `size=20` por padrão; `size` aceita de 1 a 100. As linhas ficam em `data.content`.

Cada recurso oferece pesquisa por `q` e filtros específicos na coleção e em `GET /api/search/{recurso}`. Dropdowns usam `GET /api/lookups/{recurso}`, com páginas de 5 itens e opções `{id, label}`. Os parâmetros, limites, filtros e exemplos estão em [docs/PAGINACAO_PESQUISA.md](docs/PAGINACAO_PESQUISA.md).

Os requests e responses estão nos DTOs dos módulos `warehouse`, `product` e `partner`. Criações retornam HTTP **201**, consultas/edições **200**, dados inválidos **400**, recurso ausente **404** e violação de regra de negócio **422**. Conflitos de restrição do banco retornam **409**. A inativação usa `active: false` na edição, preservando os vínculos históricos.

As permissões são verificadas na política HTTP e nos métodos dos controllers. O operador pode consultar estrutura e produtos; não pode alterar esses cadastros nem gerenciar parceiros com o perfil inicial.

## Verificação

```bash
./mvnw verify
./ops/verify-backup.sh
```

A suíte validada contém 452 testes aprovados, sem testes ignorados, incluindo dois testes MySQL/Testcontainers executados com Docker. Os testes de integração cobrem a matriz de autorização com JWTs assinados, mudanças de conta/perfil/permissões, códigos de uso único, bloqueio, recuperação, fluxo web/mobile, migrations iniciais, persistência operacional e operações reais das APIs de cadastro. Também cobrem paginação, pesquisa, filtros combinados e dropdowns dos 25 recursos. Os testes de cadastros usam H2, controllers de produção e JWTs assinados, sem rotas de apoio. Os testes MySQL de rotação concorrente e de estoque/imutabilidade usam Testcontainers e são ignorados automaticamente quando Docker não está disponível. Para repetir a validação completa, é necessário acesso ao Docker. SMTP é substituído por um sender de teste nos testes que enviam e-mail; a entrega real depende da configuração do ambiente.

## Ambiente e contêineres

Todas as propriedades do `application.yaml` usam variáveis de ambiente, listadas em [.env.example](.env.example). O Spring lê variáveis do processo; o arquivo `.env` não é carregado automaticamente na execução direta da JVM.

Para Docker Compose, usar o `.env.example` da raiz `TCC_WMS`, ao lado do `docker-compose.yml`. O Compose encaminha todos os parâmetros ao `environment` do backend e monta a URL JDBC com `DB_HOST`, `DB_PORT` e `DB_NAME` quando `DB_URL` está vazio. Os valores existentes de JWT, banco e SMTP foram conservados.

A imagem usa o Dockerfile centralizado em `../docker/backend/Dockerfile`, com este repositório como contexto, compilação Maven/Java 21 e execução com UID/GID `1001:1001`. O [.dockerignore](.dockerignore) envia apenas o POM e as fontes/recursos de produção.

Imagem padrão: `ghcr.io/gabriellourenco1912/wms-saas-backend:latest`. `GHCR_NAMESPACE` e `IMAGE_TAG`, no `.env` da raiz, alteram o namespace e a versão. `SERVER_PORT` controla a porta interna e `BACKEND_PUBLISHED_PORT` a porta publicada no host.

Consultar [docker/README.md](../docker/README.md) para configuração, build e comandos de pull/push das imagens do backend e do web.

## Backup e restauração

[ops/mysql-backup.sh](ops/mysql-backup.sh) gera dump consistente e compactado, exige retenção de ao menos 30 dias e impede execuções simultâneas. [ops/mysql-restore.sh](ops/mysql-restore.sh) exige uma base de destino vazia e diferente da base operacional. Ambos leem `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD` e `DB_NAME`; backup aceita `BACKUP_DIR`/`BACKUP_RETENTION_DAYS`, e restauração exige `RESTORE_DB_NAME`.

O exemplo [ops/wms-backup.cron.example](ops/wms-backup.cron.example) programa uma execução diária. Instalar e monitorar esse agendamento faz parte da configuração do servidor de destino; nenhum cron de produção foi instalado por este trabalho.

`./ops/verify-backup.sh` usa um contêiner MySQL 8.4 isolado, aplica V1/V2, executa os scripts reais e confere restauração de estrutura, dados e gatilho, retenção e rejeição de destinos inseguros. A verificação passou nesta entrega e remove seu contêiner ao terminar; não acessa a base configurada da aplicação.
