# Paginação, pesquisa e dropdowns

O contrato segue as rotas de pesquisa e lookup de `inatback/main`. Os predicados e os DTOs permanecem nos módulos responsáveis pelo recurso. As consultas de entidades filtram e paginam no banco; somente o catálogo finito de permissões, definido em enum, usa paginação em memória.

## Rotas e parâmetros comuns

| Finalidade | Rota | Página inicial | Tamanho padrão | Tamanho permitido |
| --- | --- | --- | --- | --- |
| Listagem com filtros | `GET /api/{recurso}` | `page=0` | `size=20` | 1 a 100 |
| Pesquisa com os mesmos filtros | `GET /api/search/{recurso}` | `page=0` | `size=20` | 1 a 100 |
| Dropdown | `GET /api/lookups/{recurso}` | `page=0` | `size=5` | 1 a 5 |

`q` é opcional, aceita até 160 caracteres e pesquisa por trecho nos campos indicados abaixo, sem diferenciar maiúsculas e minúsculas. Espaços externos são removidos e um texto vazio não restringe a consulta. Os caracteres `%`, `_` e `!` são tratados literalmente, sem permitir curingas enviados pelo cliente. Os filtros específicos são combinados com `q` usando **AND**; os campos cobertos por `q` são combinados usando **OR**.

As páginas têm ordenação crescente pelo identificador único (`id`, `ulid` em usuários e `code` em perfis/permissões), garantindo uma ordem determinística para um conjunto de dados estável. Não há ordenação arbitrária por propriedade via query string. Alterações concorrentes nos registros podem mudar a composição das páginas entre requisições.

Tamanho zero, negativo ou acima do limite, página negativa, tipos inválidos, `q` longo demais e deslocamento `page * size` acima de 2.147.483.647 retornam **400**, sem corrigir silenciosamente o pedido. Uma página válida além do resultado retorna `content: []`, conservando o total real.

Embalagens conservam a coleção `GET /api/products/{productId}/packages`, agora paginada, e também oferecem `/api/search/product-packages` e `/api/lookups/product-packages`, filtráveis por `productId`. Quando informado, o produto precisa existir; caso contrário, essas consultas retornam **404**. O histórico de uma tarefa pode ser paginado em `GET /api/tasks/{id}/history`. LPNs podem ser consultados em `/api/lpns` e `/api/lpns/{id}`.

## Respostas

Listas e pesquisas retornam o DTO habitual de cada recurso dentro de `data.content`. Todas as respostas paginadas contêm:

```json
{
  "data": {
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "first": true,
    "last": true
  }
}
```

O envelope `ApiResponse` também conserva `timestamp`, `status` e `message`. Dropdowns usam a mesma estrutura de página, com `size: 5` por padrão e conteúdo compacto:

```json
{
  "id": "identificador-do-registro",
  "label": "SKU-001 — Produto de exemplo"
}
```

O `id` é o valor enviado posteriormente nos comandos do sistema. Em perfis, é o código, como `OPERADOR`; em permissões, é o código, como `PRODUCT_VIEW`. O dropdown não substitui a validação dos comandos: disponibilidade e autorização são novamente verificadas ao executar a operação.

As coleções de detalhe de um registro, como itens do pedido, conteúdo de uma tarefa e linhas de um inventário, continuam completas no DTO de detalhe. Paginar a coleção principal não corta os itens internos de uma operação.

## Filtros por recurso

As rotas de coleção, pesquisa e lookup de cada recurso compartilham seus filtros. As listagens gerais incluem ativos e inativos quando `active` é omitido. Lookups de cadastros e perfis assumem `active=true`; `active=false` consulta os inativos. Lookups de usuários assumem `status=ACTIVE` e exigem perfil ativo e e-mail verificado nesse estado.

| Recurso | Campos pesquisados por `q` | Filtros específicos |
| --- | --- | --- |
| `warehouses` | ID, nome, documento e endereço | `active` |
| `zones` | ID, nome, tipo de controle e ID do armazém | `warehouseId`, `active` |
| `positions` | ID, código, tipo, status, finalidade e ID da zona | `zoneId`, `warehouseId`, `status`, `type`, `purpose`, `active` |
| `categories` | ID, nome e descrição | `active` |
| `products` | ID, SKU, nome, unidade base e barcode das embalagens | `categoryId`, `sku`, `barcode`, `lotControlled`, `expiryControlled`, `active` |
| `product-packages` | ID, ID do produto, tipo e barcode | `productId`, `type`, `barcode`, `active` |
| `partners` | ID, razão social, documento e tipo | `type`, `taxId`, `active` |
| `pre-receipts`, `receipts` | ID e número do documento de entrada | `warehouseId`, `status`, `partnerId`, `documentNumber`, `productId` |
| `putaway-tasks` | ID, status, tipo, ID do pedido e do LPN | `warehouseId`, `status`, `assignedUserId`, `orderId`, `productId`, `lpnId` |
| `orders` | ID e número do documento de saída | `warehouseId`, `status`, `partnerId`, `documentNumber`, `productId` |
| `waves` | ID e código | `warehouseId`, `status`, `code`, `orderId` |
| `picking-tasks` | ID, status, tipo, ID do pedido e do LPN | `warehouseId`, `status`, `assignedUserId`, `orderId`, `productId`, `waveId` |
| `divergences` | ID, motivo, tipo e status | `warehouseId`, `status`, `type`, `taskId`, `stockId` |
| `shipments` | ID, status e ID do pedido | `warehouseId`, `status`, `orderId`, `dockPositionId` |
| `stock` | ID, lote, SKU/nome do produto, código do LPN e da posição | `warehouseId`, `productId`, `positionId`, `lpnId`, `lot`, `blockStatus` |
| `stock-blocks` | ID, tipo, lote e motivo | `warehouseId`, `stockId`, `productId`, `positionId`, `lot`, `type`, `active` |
| `tasks`, `transfers` | ID, tipo, status e motivo de cancelamento | `warehouseId`, `type`, `status`, `assignedUserId`, `orderId`, `productId`, `lpnId`, `waveId`, `originPositionId`, `destinationPositionId` |
| `stock-transactions` | ID, lote registrado e motivo | `warehouseId`, `stockId`, `productId`, `operationId`, `movementType`, `userId`, `lpnId`, `lot`, `from`, `to` |
| `lpns` | ID, código de rastreio e status | `warehouseId`, `positionId`, `outboundOrderId`, `status` |
| `inventories` | ID, status e ID do responsável | `warehouseId`, `positionId`, `status`, `responsibleUserId` |
| `users` | ID, nome, e-mail, nome e código do perfil | `profileCode`, `status` |
| `profiles` | Código e nome | `active` |
| `permissions` | Código e requisito funcional | `resource` (prefixo como `PRODUCT`), `functionalRequirement` |

Os filtros específicos com valor vazio não restringem a consulta. Booleanos e
estados de usuário precisam ter valores válidos; os demais estados/tipos são
códigos exatos do domínio, como `ARMAZENAGEM` ou `PENDENTE`. Não há pesquisa por
trecho nesses filtros: usar `q` para essa finalidade.

`pre-receipts`/`receipts` restringem a consulta a documentos de entrada;
`orders`, a documentos de saída. `putaway-tasks`, `picking-tasks` e `transfers`
preservam o tipo da tarefa, mesmo quando há pesquisa textual ou outros filtros.
O filtro `productId` de putaway também encontra produtos de LPNs mistos.
Nas tarefas históricas, os lançamentos e eventos imutáveis identificam os
produtos originais, inclusive em cancelamentos de tarefas com LPN misto.
Produtos acrescentados ao LPN posteriormente não são atribuídos à tarefa antiga.

No livro razão, `stockId` e `lpnId` encontram vínculos de origem ou destino;
`from` e `to` são instantes ISO 8601 inclusivos, por exemplo
`2026-10-15T00:00:00Z`. Um intervalo invertido retorna **400**. O filtro de
armazém considera as posições de origem/destino. Em LPNs expedidos, o armazém
também pode ser identificado pelo pedido de saída, preservando a consulta
histórica depois que o LPN deixa de ter posição física.

`GET /api/tasks/{id}/history` aceita os parâmetros comuns de paginação e `q`
sobre ID, status anterior/novo e motivo. A consulta é sempre limitada à tarefa
da rota; uma tarefa inexistente retorna **404**.

## Exemplos

```text
GET /api/products?page=0&size=20
GET /api/search/products?q=arroz&categoryId=<id>&active=true&page=1&size=10
GET /api/lookups/products?q=arroz&categoryId=<id>&page=0
GET /api/search/positions?q=A-01&warehouseId=<id>&purpose=ARMAZENAGEM
GET /api/lookups/partners?q=distribuidora&type=FORNECEDOR
GET /api/lookups/product-packages?productId=<id>&q=789
GET /api/search/users?q=joao&profileCode=OPERADOR&status=ACTIVE
GET /api/search/permissions?resource=PRODUCT&functionalRequirement=RF004&q=VIEW
```

Substituir `<id>` por um identificador real e codificar valores da query string na URL. Todos os exemplos exigem `Authorization: Bearer <accessToken>` e a permissão de leitura correspondente.

## Autorização e integração dos clientes

Cada rota de pesquisa e lookup tem uma regra explícita em `WmsEndpointPolicy`, com a mesma permissão da consulta do recurso. Usuários e perfis exigem `USER_VIEW` e `PROFILE_VIEW`; livro razão exige `AUDIT_VIEW`. Não existe endpoint genérico que escolha tabelas ou propriedades a partir de entrada do cliente. Recursos não previstos continuam sem acesso.

Os clientes devem obter as linhas em `data.content` e avançar `page` enquanto `last=false`. Essa mudança de formato também se aplica às coleções anteriormente sem paginação: **perfis, permissões e embalagens de produto**. Não usar `data` diretamente como um array nessas três consultas.
