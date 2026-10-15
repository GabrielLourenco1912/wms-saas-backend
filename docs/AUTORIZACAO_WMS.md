# Autenticação e autorização do WMS

## Fonte e escopo

Fonte principal: `../docs-gerais/Artigo_WMS_Versao_Final_Revisada_v2.docx`, seções 4.1, 4.2 e 4.4, complementada por `../docs-gerais/diagrama_caso_uso_wms_revisado.md`.

Esta documentação descreve o contrato de autenticação e autorização. O estado dos módulos operacionais e a organização para continuar seu desenvolvimento estão em [ARQUITETURA.md](ARQUITETURA.md); os limites descritos abaixo não substituem a validação funcional desses módulos.

A implementação na `main` reaproveita a autenticação da branch `auth` (`049ef93`) e conserva os pacotes `dev.lourencogabriel.wmssaas`. O domínio educacional, seus cadastros, organizações, pessoas, notificações e importações não fazem parte da adaptação.

Estão implementados autenticação, contas, perfis e permissões. As rotas logísticas em `WmsEndpointPolicy` são **contratos de autorização**: a declaração da rota nessa matriz não implementa sua regra de negócio. Os módulos operacionais possuem código parcial, conforme [ARQUITETURA.md](ARQUITETURA.md). Os testes da matriz usam controllers exclusivos de teste onde não há rota de produção e verificam autorização, sem comprovar o funcionamento dos processos logísticos.

## Modelo de acesso

Cada usuário possui um perfil operacional, `GESTOR` ou `OPERADOR`. O perfil tem nome, indicador de ativo e conjunto de permissões persistido. O RF016 é atendido pelos endpoints de cadastro/edição de usuários e configuração das permissões dos dois perfis. O artigo define esses dois atores; a criação de outros tipos de perfil não foi acrescentada.

Uma permissão representa uma ação concreta, por exemplo `PRODUCT_VIEW`, `PRODUCT_CREATE`, `PRODUCT_EDIT`, `PICKING_EXECUTE` ou `ORDER_CANCEL`. Ter a permissão de consultar não concede a permissão de executar, editar ou cancelar. O nome do perfil não funciona como liberação irrestrita: a requisição usa suas permissões atuais.

Para acessar os recursos protegidos, a conta precisa estar `ACTIVE`, ter e-mail verificado e perfil ativo. `INVITED` permite autenticação por senha para iniciar a confirmação de e-mail; `LOCKED` e `DISABLED` impedem acesso. O JWT deve ter assinatura, emissor, prazo de validade e `token_version` válidos. O identificador do usuário é o `sub` do token. Perfis e permissões vêm do banco a cada requisição, nunca de parâmetros enviados pelo cliente.

O header `X-Client-Type: WEB|MOBILE` escolhe o transporte do refresh token; não muda o perfil nem concede permissões. Ambos os clientes podem autenticar credenciais válidas, mas cada ação continua sujeita ao perfil.

## Matriz inicial por requisito funcional

Os conjuntos abaixo são os valores iniciais da migração V1 consolidada de desenvolvimento. O gestor pode configurar permissões pelo RF016, sujeito à proteção da administração descrita adiante.

| RF | Funcionalidade | Gestor | Operador | Permissões principais |
| --- | --- | --- | --- | --- |
| RF001 | Autenticar operador mobile | Credenciais e perfil ativo | Credenciais e perfil ativo | Autenticação seguida de autorização por ação |
| RF002 | Autenticar gestor web | Credenciais e perfil ativo | Credenciais e perfil ativo | O tipo do cliente não substitui o perfil |
| RF003 | Estrutura: armazéns, zonas, posições | Consultar, criar, editar | Consultar posições/estrutura para operação | `WAREHOUSE_VIEW/CREATE/EDIT` |
| RF004 | Produtos e embalagens | Consultar, criar, editar | Consultar identificação e rastreabilidade | `PRODUCT_VIEW/CREATE/EDIT` |
| RF005A | Pré-recebimento documental | Consultar, criar/importar, editar | Sem acesso ao planejamento documental | `PRE_RECEIPT_VIEW/CREATE/EDIT` |
| RF005B | Conferência de recebimento | Consultar | Consultar e executar conferência | `RECEIPT_VIEW/EXECUTE` |
| RF006 | Roteirização de putaway | Consultar tarefas | Consultar tarefas | `TASK_VIEW`; geração é processamento interno |
| RF007 | Executar putaway | Consultar | Consultar, iniciar e concluir | `PUTAWAY_VIEW/EXECUTE` |
| RF008 | Pedidos e ondas de separação | Consultar, criar, editar pedidos; gerar ondas | Consultar suas tarefas de picking pelo módulo operacional | `ORDER_VIEW/CREATE/EDIT`, `WAVE_VIEW/CREATE`, `PICKING_VIEW` |
| RF009 | Executar picking | Consultar; autorizar exceção de quantidade | Consultar, iniciar e concluir | `PICKING_VIEW/EXECUTE`, `TASK_QUANTITY_OVERRIDE` |
| RF010 | Reportar divergência | Consultar o acompanhamento | Reportar divergência operacional | `PICKING_REPORT_DIVERGENCE` |
| RF011 | Transferências internas | Consultar | Consultar, executar e confirmar | `TRANSFER_VIEW/EXECUTE` |
| RF012 | Bloquear lotes/saldo e posições | Bloquear e desbloquear | Sem bloqueio administrativo manual | `STOCK_BLOCK/UNBLOCK` |
| RF013 | Conferir/despachar carga | Consultar | Consultar, conferir e despachar | `SHIPMENT_VIEW/EXECUTE` |
| RF014 | Saldo e auditoria | Consultar saldo e histórico completo | Consultar saldo para execução | `STOCK_VIEW`, `AUDIT_VIEW` |
| RF015 | Cancelar pedidos, ondas e tarefas pendentes | Cancelar | Sem cancelamento administrativo | `ORDER_CANCEL`, `WAVE_CANCEL`, `TASK_CANCEL` |
| RF016 | Usuários e perfis | Consultar/criar/editar usuários, atribuir perfil e configurar permissões | Consultar apenas o próprio contexto | `USER_VIEW/CREATE/EDIT/PROFILE_ASSIGN`, `PROFILE_VIEW/EDIT` |
| RF017 | Inventário | Consultar e autorizar ajuste manual | Consultar e registrar contagens | `INVENTORY_VIEW/EXECUTE/ADJUST` |

As consultas operacionais concedidas ao gestor permitem acompanhar os processos. As consultas de estrutura, produtos, saldo e tarefas concedidas ao operador fornecem os dados necessários à execução. Elas não autorizam alterar os cadastros.

Os cadastros de categorias (`/api/categories`) usam `PRODUCT_VIEW/CREATE/EDIT`, como os produtos. Clientes e fornecedores (`/api/partners`) usam `ORDER_VIEW/CREATE/EDIT`, mantendo o contrato de permissões do cadastro de parceiros existente. Ambas as famílias de rotas constam explicitamente de `WmsEndpointPolicy`; a negação padrão continua protegendo rotas sem classificação. Os testes de integração de cadastros exercitam os controllers de produção, incluindo a revogação de permissões com um JWT previamente emitido.

## Rastreabilidade das regras de negócio

| RN | Regra de autorização aplicada |
| --- | --- |
| RN001 | Conta autenticada, ativa, e-mail verificado, perfil ativo; JWT válido; sessão invalidada quando a versão muda. |
| RN002 | Livro de estoque disponível somente para consulta com `AUDIT_VIEW`. POST/PUT/PATCH/DELETE diretos não são liberados a nenhum perfil. Transações compensatórias devem ser criadas internamente pelos casos de uso autorizados. A imutabilidade no banco ainda pertence à implementação do módulo de estoque. |
| RN003 | Somente `STOCK_BLOCK/UNBLOCK` permite bloqueio administrativo. Operadores podem reportar divergências; o bloqueio preventivo automático da RN009 pertence ao serviço de picking. |
| RN004, RN005 | Putaway e transferência exigem suas permissões de execução; capacidade e atualização integrada de LPN continuam sendo validações do serviço logístico. |
| RN006 | Conferência/contagem permitida ao operador; configuração de rastreio exige `PRODUCT_EDIT`. A obrigatoriedade de lote/validade deverá ser validada no respectivo serviço. |
| RN007, RN013 | Geração de onda exige `WAVE_CREATE`; alocação, FEFO/FIFO e reserva são efeitos internos dessa ação. Não existe perfil público `SISTEMA` nem endpoint de reserva irrestrito. |
| RN008 | Execução exige `PUTAWAY_EXECUTE` ou `PICKING_EXECUTE`. `TASK_QUANTITY_OVERRIDE` representa autorização administrativa de excesso; não elimina conferência, limites nem tratamento da divergência. |
| RN009 | `PICKING_REPORT_DIVERGENCE` permite comunicar falta. Bloqueio preventivo, realocação e ajuste da tarefa são efeitos internos, sem conceder ao operador permissões administrativas de estoque. |
| RN010 | `SHIPMENT_EXECUTE` permite conferência/expedição; atendimento integral do pedido é verificação obrigatória do serviço. |
| RN011 | Cancelamento exige permissão específica de pedido/onda/tarefa; operador não pode cancelar planejamento. O serviço deverá preservar histórico e cancelar apenas tarefas elegíveis. |
| RN012 | Permissões separadas por visualização, criação, edição, execução e cancelamento; filtragem HTTP mais segurança de métodos; rotas sem regra são negadas. |
| RN014 | Permissão autoriza a ação; não autoriza qualquer transição de status. O futuro serviço de tarefas deverá validar origem/destino do status e vínculo da execução. |
| RN015 | Permissão de execução não dispensa leitura de SKU, LPN ou posição, a ser validada no caso de uso operacional. |
| RN016 | Operador pode contar com `INVENTORY_EXECUTE`; ajuste manual exige `INVENTORY_ADJUST`. Ajustes automáticos da reconciliação devem ocorrer dentro da execução autorizada, sem edição do livro razão. |
| RN017 | Somente gestor cria expectativa com `PRE_RECEIPT_CREATE`; operador confirma mercadoria com `RECEIPT_EXECUTE`. Permissão de pré-recebimento não concede conferência nem geração direta de saldo. |

**Decisões de desenho:** o artigo não enumera nomes de permissões, não separa ajuste manual de automático nem prescreve proteção do último gestor. A matriz aplica a divisão entre planejamento do gestor e execução do operador. A permissão específica para excesso deriva da RN008; a separação de contagem e ajuste manual deriva da RN016. Esses pontos podem ser configurados pelo gestor sem alterar as validações logísticas.

## Administração de contas e perfis

- `GET /api/users/me`: contexto do usuário autenticado, incluindo permissões efetivas; não expõe senha, hash ou versão da sessão.
- `GET /api/users` e `GET /api/users/{id}`: `USER_VIEW`; listagem paginada.
- `POST /api/users`: `USER_CREATE` e `USER_PROFILE_ASSIGN`; recebe `name`, `email`, `password`, `profileCode`; cria `INVITED` e envia código de verificação.
- `POST /api/auth/register`: compatibilidade com a rota anterior, com as mesmas permissões; cadastro público foi encerrado conforme RF016.
- `PUT /api/users/{id}`: `USER_EDIT`; recebe `name`, `email`, `status`, `profileCode`; mudar perfil exige também `USER_PROFILE_ASSIGN`.
- `GET /api/profiles`, `GET /api/profiles/{code}` e `GET /api/permissions`: `PROFILE_VIEW`.
- `PUT /api/profiles/{code}`: `PROFILE_EDIT`; recebe `name`, `active`, `permissions`.

Trocar e-mail exige nova verificação: uma conta com e-mail novo não pode ser marcada `ACTIVE` administrativamente. Mudanças de e-mail, perfil ou status incrementam `token_version`, revogam refresh tokens e invalidam códigos pendentes. Desativar um perfil também revoga as sessões de seus usuários; reativá-lo não restaura os tokens antigos. Alterar permissões de um perfil ativo é aplicado na requisição seguinte.

O último gestor ativo não pode ser desativado ou rebaixado por uma edição administrativa. A operação usa bloqueio do perfil de gestor para serializar rebaixamentos concorrentes. O perfil `GESTOR` conserva acesso ativo e as seis permissões de administração de usuários/perfis. Contas são desativadas por status, sem endpoint de exclusão física, preservando referências para a auditoria futura.

Não foi criada atribuição de usuários a armazéns nem isolamento entre empresas: o artigo disponível não especifica as regras desses vínculos. As permissões controlam funcionalidades; acesso a registros por empresa/armazém e responsabilidade sobre tarefas deve ser integrado quando esses modelos e restrições forem definidos.

## Fluxo de autenticação reaproveitado

1. `POST /api/auth/login` recebe `email` e `password`, normaliza o e-mail e verifica BCrypt. Retorna **202**, com `challengeId`, `purpose`, `maskedEmail`, `expiresAt` e `maxAttempts`.
2. Um código de seis dígitos é enviado por SMTP. Na primeira autenticação, confirma o e-mail; nas seguintes, confirma o login (`MFA_LOGIN`). Somente digest HMAC e nonce são persistidos, nunca o código em texto claro.
3. `POST /api/auth/challenge/verify` recebe `challengeId` e `code`. Verificação bem-sucedida retorna JWT de 15 minutos e refresh token de 30 dias. O código tem uso único, expiração padrão de 10 minutos e limite de tentativas.
4. No cliente WEB, refresh fica em cookie HttpOnly, SameSite=Lax, caminho `/api/auth`, Secure configurável. No MOBILE, retorna no corpo e deve ser armazenado pelo cliente com proteção apropriada.
5. `POST /api/auth/refresh` rotaciona o refresh token, cujo hash SHA-256 fica no banco; usa bloqueio pessimista para impedir reutilização concorrente.
6. `POST /api/auth/logout` revoga o refresh apresentado e expira o cookie web. O JWT de acesso dessa sessão permanece válido até expirar, salvo invalidação de conta/perfil/versão; esse é o comportamento reaproveitado da branch `auth`.
7. `POST /api/auth/forgot-password` recebe `email`; `POST /api/auth/reset-password` recebe `challengeId`, `code`, `newPassword`. Redefinir senha revoga as sessões anteriores. Um desafio de recuperação não pode ser usado como login.

Após cinco falhas de senha/código, a conta recebe bloqueio temporário de 15 minutos. As falhas são persistidas mesmo quando a autenticação retorna erro. O desbloqueio ocorre no próximo login elegível ou pelo agendador. Bloqueios administrativos têm duração indefinida. Senhas novas exigem ao menos oito caracteres e no máximo 72 bytes UTF-8, limite do BCrypt (RNF003).

Respostas de autenticação inválida usam 401. Falta de permissão usa 403. Violação de regra administrativa usa 422. Payload inválido usa 400. Falha no envio do código usa 503 e desfaz a operação que dependia do envio.

## Integração dos próximos módulos

`WmsEndpointPolicy.RULES` contém os contratos por método HTTP e caminho. Rotas diferentes ou novos métodos precisam de uma regra explícita; `anyRequest().denyAll()` impede liberação implícita.

Além do filtro HTTP, aplicar autorização ao serviço do caso de uso:

```java
@PreAuthorize("@wmsAuthorization.can(authentication, 'PICKING_EXECUTE')")
@Transactional
public void completePicking(...) {
    // Validar tarefa, status, leitura, quantidade, reserva e transação de estoque.
}
```

A execução interna do motor ocorre a partir de uma ação já autorizada, sem receber um papel privilegiado informado pelo cliente. Validações de quantidade, lote, capacidade, estado, vínculo de tarefa e estoque bloqueado permanecem obrigatórias mesmo quando há permissão.
