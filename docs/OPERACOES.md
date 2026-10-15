# Contratos operacionais da API

Todas as rotas `/api` exigem `Authorization: Bearer <accessToken>`, salvo as rotas públicas de autenticação documentadas no README. Respostas usam `ApiResponse`; listas paginadas usam `data.content`, `page`, `size`, `totalElements` e `totalPages`. Datas de validade usam `YYYY-MM-DD`; eventos usam UTC. Quantidades são decimais com até seis casas, na unidade base do produto, exceto a conferência de recebimento que informa quantidade da embalagem escolhida.

Todas as coleções aceitam `page` (inicial 0), `size` (padrão 20, máximo 100), `q` e filtros do recurso. `GET /api/search/{recurso}` compartilha a consulta da coleção; `GET /api/lookups/{recurso}` retorna opções `{id, label}` em páginas de 5 itens. Ver [contrato de paginação e pesquisa](PAGINACAO_PESQUISA.md) para filtros, exemplos e mudanças no formato de perfis, permissões e embalagens.

## Idempotência

- Recebimento, putaway, estoque, transferências, compensações, cancelamento genérico de tarefas e inventário usam o header **`Idempotency-Key`**.
- Ondas, picking, divergências, autorização de excesso, cancelamento de pedido/onda e operações de expedição usam **`idempotencyKey` no JSON**, conforme `OutboundDtos`.
- Usar uma chave nova por comando e conservar a chave, usuário, tipo e payload em um reenvio. Um comando confirmado retorna o resultado armazenado sem repetir efeitos; uma chave reaproveitada para conteúdo/identidade diferente é rejeitada. Uma transação que falha não confirma efeitos parciais.
- O hash é calculado sobre JSON canônico: a ordem das propriedades não importa; valores e ordem dos arrays importam. Depois de timeout, consultar o estado ou reenviar o mesmo comando com a mesma chave.

## Entrada e armazenagem

| Ação | Rota | Permissão |
| --- | --- | --- |
| Listar/consultar expectativa | `GET /api/pre-receipts`, `GET /api/pre-receipts/{id}` | `PRE_RECEIPT_VIEW` |
| Criar/editar expectativa | `POST /api/pre-receipts`, `PUT /api/pre-receipts/{id}` | `PRE_RECEIPT_CREATE/EDIT` |
| Importar expectativa NF-e | `POST /api/pre-receipts/import` | `PRE_RECEIPT_CREATE` |
| Listar/consultar recebimentos | `GET /api/receipts`, `GET /api/receipts/{id}` | `RECEIPT_VIEW` |
| Confirmar recebimento | `POST /api/receipts/{id}/confirm` | `RECEIPT_EXECUTE` |
| Consultar putaway | `GET /api/putaway-tasks`, `GET /api/putaway-tasks/{id}` | `PUTAWAY_VIEW` |
| Iniciar/concluir putaway | `POST /api/putaway-tasks/{id}/start`, `POST /api/putaway-tasks/{id}/complete` | `PUTAWAY_EXECUTE` |

Criar a expectativa com `warehouseId`, `partnerId`, `documentNumber`, `expectedAt` opcional e `items: [{productId, quantity}]`. Isso não cria saldo. O detalhe retorna `receipt`, `items` e `confirmations`.

Na confirmação, informar `orderItemId`, `packagingId`, `quantity`, `lot`/`expiryDate` quando exigidos, `lpnCode`, `positionId` da doca de recebimento e `scannedProductCode`, `scannedLpnCode`, `scannedPositionCode`. A resposta contém `confirmation`, `stockId` e `putawayTask`. O servidor converte à unidade base, registra saldo/livro e planeja a tarefa na mesma transação.

O início de putaway usa somente o header de idempotência. A conclusão recebe `actualQuantity`, `scannedLpnCode` e `scannedDestinationCode`; o destino é o planejado na tarefa. Consultar `contents` para as camadas e quantidades do LPN. A movimentação integral preserva lote, validade e data original de entrada.

A importação recebe `warehouseId`, `partnerId`, `invoiceXml` e `expectedAt` opcional. Faz correspondência com SKU/embalagens cadastrados e rejeita entidades XML externas; não é um serviço de autorização fiscal.

## Saída

| Ação | Rota | Permissão |
| --- | --- | --- |
| Pedidos | `GET/POST /api/orders`, `GET/PUT /api/orders/{id}` | `ORDER_VIEW/CREATE/EDIT` |
| Cancelar pedido | `POST /api/orders/{id}/cancel` | `ORDER_CANCEL` |
| Ondas | `GET/POST /api/waves`, `GET /api/waves/{id}` | `WAVE_VIEW/CREATE` |
| Cancelar onda | `POST /api/waves/{id}/cancel` | `WAVE_CANCEL` |
| Picking | `GET /api/picking-tasks`, `GET /api/picking-tasks/{id}` | `PICKING_VIEW` |
| Iniciar/concluir picking | `POST /api/picking-tasks/{id}/start`, `POST /api/picking-tasks/{id}/complete` | `PICKING_EXECUTE` |
| Reportar falta | `POST /api/picking-tasks/{id}/divergences` | `PICKING_REPORT_DIVERGENCE` |
| Consultar divergências | `GET /api/divergences`, `GET /api/divergences/{id}` | `PICKING_VIEW` |
| Autorizar novo alvo | `POST /api/tasks/{id}/authorize-excess` | `TASK_QUANTITY_OVERRIDE` |
| Expedições | `GET/POST /api/shipments`, `GET /api/shipments/{id}` | `SHIPMENT_VIEW/EXECUTE` |
| Conferir/despachar | `POST /api/shipments/{id}/confirm`, `POST /api/shipments/{id}/dispatch` | `SHIPMENT_EXECUTE` |
| Cancelar preparação | `POST /api/shipments/{id}/cancel` | `ORDER_CANCEL` |

Criar pedido com `warehouseId`, `partnerId`, `documentNumber`/`expectedAt` opcionais e `items: [{productId, quantity}]`. Criar onda com `warehouseId`, `code`, `orderIds`, `priority` opcional e `idempotencyKey`. A onda aloca somente demanda residual e saldo elegível, seguindo FEFO/FIFO e reservando por tarefa.

O início do picking recebe `idempotencyKey`. A conclusão recebe também `actualQuantity`, `sourceLpnCode`, `sourcePositionCode`, `productCode`, `destinationLpnCode`, `destinationPositionId` e `destinationPositionCode`. O destino deve ser uma doca de expedição do mesmo armazém. A coleta move o saldo para preparação; não faz a baixa externa.

A falta usa os mesmos identificadores de origem, `actualQuantity` menor que o alvo, `reason` e os dados de destino quando houver coleta. O saldo remanescente é bloqueado e a reposição procura outro endereço. Excesso exige autorização administrativa com `newTargetQuantity`, `reason` e `idempotencyKey`, respeitando saldo e demanda residual.

Preparar expedição com `orderId`, `dockPositionId`, `lpnIds` e `idempotencyKey`. Conferir cada LPN com `lpnCode`, `dockPositionCode` e chave; despachar com `dockPositionCode` e chave. Só o despacho baixa fisicamente os saldos e aumenta o atendimento dos itens.

Cancelamentos exigem `reason` e `idempotencyKey`; tarefas em execução precisam ser concluídas ou tratadas antes. Mercadoria preparada de pedido cancelado retorna por transferência do LPN inteiro à armazenagem. O servidor reduz a quantidade preparada, retira o vínculo de saída e acrescenta `RETORNO` ao livro. Mercadoria já despachada volta por um recebimento documental.

## Estoque, tarefas e auditoria

| Ação | Rota | Permissão |
| --- | --- | --- |
| Saldos | `GET /api/stock`, `GET /api/stock/{id}` | `STOCK_VIEW` |
| Bloqueios | `GET /api/stock-blocks` | `STOCK_VIEW` |
| Bloquear/liberar saldo ou posição | `POST /api/stock/{id}/block`, `/unblock`; `POST /api/positions/{id}/block`, `/unblock` | `STOCK_BLOCK/UNBLOCK` |
| Bloquear lote/liberar bloqueio | `POST /api/stock-blocks/lots`, `POST /api/stock-blocks/{id}/release` | `STOCK_BLOCK/UNBLOCK` |
| Transferências | `GET/POST /api/transfers`, `GET /api/transfers/{id}`, `POST /api/transfers/{id}/confirm` | `TRANSFER_VIEW/EXECUTE` |
| Tarefas e histórico | `GET /api/tasks`, `GET /api/tasks/{id}` | `TASK_VIEW` |
| Cancelar tarefa pendente | `POST /api/tasks/{id}/cancel` | `TASK_CANCEL` |
| Livro razão | `GET /api/stock-transactions`, `GET /api/stock-transactions/{id}` | `AUDIT_VIEW` |
| Compensação | `POST /api/stock-transactions/{id}/compensations` | `INVENTORY_ADJUST` |

Bloqueio/liberação e cancelamento de tarefa recebem `{reason}`. Bloqueio de lote recebe `warehouseId`, `productId`, `lot`, `reason`. Planejar transferência exige exatamente um entre `lpnId` e `stockId`, `destinationPositionId` e `quantity`; na transferência parcial informar também `destinationLpnCode`.

Confirmar transferência com `actualQuantity`, `scannedLpnCode`, `scannedOriginCode`, `scannedDestinationCode` e `scannedProductCode` para um saldo específico. Transferência integral exige todos os conteúdos e rejeita plano cuja origem ficou desatualizada. Compensação recebe `quantity`, `reason`, `scannedLpnCode`, `scannedPositionCode`, `scannedProductCode`; preserva o lançamento original e limita o total compensado. Relocações são corrigidas por transferência inversa real. Não há endpoint de edição/exclusão do livro.

## Inventário

`GET /api/inventories` e `GET /api/inventories/{id}` exigem `INVENTORY_VIEW`. Os comandos usam `Idempotency-Key` no header:

1. `POST /api/inventories`: `positionId`, `scannedPositionCode` e `responsibleUserId` opcional; exige `INVENTORY_EXECUTE`. O responsável precisa estar ativo e habilitado. Saldos reservados ou tarefas ativas precisam ser resolvidos antes da abertura.
2. `POST /api/inventories/{id}/counts`: `scannedPositionCode` e `items`. Cada item informa `inventoryItemId` do snapshot, `lpnCode`, `scannedProductCode` e `countedQuantity`. Para um achado novo, omitir `inventoryItemId` e informar `productId`, lote e validade quando exigidos. Contagem é restrita ao responsável.
3. `POST /api/inventories/{id}/adjust`: `scannedPositionCode`, `reason`; exige `INVENTORY_ADJUST`. Todas as linhas precisam ter contagem. Diferenças positivas/negativas geram ajustes; zero não gera lançamento fictício. Aplicação e reenvio não duplicam efeito.
4. `POST /api/inventories/{id}/cancel`: `scannedPositionCode`; exige `INVENTORY_EXECUTE` ou `INVENTORY_ADJUST`, e responsável ou gestor autorizado a ajustar. Preserva contagens e histórico.

A posição permanece congelada em `PENDENTE`/`EM_CONTAGEM`, sendo liberada em `CONCLUIDO`/`CANCELADO`. Saldos históricos zerados não impedem uma nova contagem de doca após despacho.
