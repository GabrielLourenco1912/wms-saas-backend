# Dados sintéticos e isolamento da homologação

A branch `homol` acrescenta um perfil Spring explícito e uma migration exclusiva
para a aceitação técnica do TCC. As migrations comuns são V1/V2/V3; V3 amplia o resultado idempotente para ondas de até 200 pedidos. Nenhuma migration sintética integra o perfil de produção.

`application-homol.yaml` inclui `classpath:db/homol` além das migrations comuns.
Somente esse perfil descobre `V1000__homologation_synthetic_dataset.sql`. Antes de
qualquer migration, `HomologationDatabaseGuard` recusa catálogos diferentes de
`wms_saas_homol` e `wms_acceptance_*`.

O conjunto inicial contém um gestor, dois operadores, armazém/zona, docas e
posições, três produtos/embalagens, fornecedor/cliente, camadas FEFO/FIFO e uma
camada para carga. Os saldos iniciais possuem lançamentos sintéticos no livro
razão. Há um documento de entrada e um de saída pendentes para exercício manual;
nenhum saldo é criado pelo documento de entrada. Os identificadores estão em
[HOMOLOGACAO_FIXTURES.json](HOMOLOGACAO_FIXTURES.json).

As contas são `gestor@homol.wms.local`, `operador@homol.wms.local` e
`concorrente@homol.wms.local`. As senhas não são fixas nem versionadas: a migration
recebe os hashes BCrypt em `HOMOL_MANAGER_PASSWORD_HASH` e
`HOMOL_OPERATOR_PASSWORD_HASH`. O segundo hash atende aos dois operadores.
O login continua exigindo o código SMTP real. Não foram adicionados endpoints
de bypass, redefinição de banco ou leitura de códigos de autenticação.

Somente na base sintética, o operador também consulta pedidos e auditoria para
exercitar seletores de expedição e leitura do histórico. As permissões de
produção não são alteradas.

`HomologationMigrationIntegrationTest` aplica V1/V2/V3/V1000 em MySQL 8.0 descartável,
confere hashes/quantidades, proteção do livro e nova execução sem duplicar dados.
Dez casos de `HomologationDatabaseGuardTest` verificam nomes permitidos e a recusa
de catálogos de produção/ambíguos. Em 06/11/2026, `./mvnw verify` concluiu
**464 testes sem falhas ou ignorados**, incluindo as três suítes MySQL/Docker.

O Compose e a automação de aceitação pertencem à infraestrutura. Consulte
[docker/HOMOLOGACAO.md](../../docker/HOMOLOGACAO.md) para os destinos, os testes
web/Android, as medições e os artefatos. Os testes criam documentos novos por
execução; não apagam livro razão nem usam o banco de produção.

## Correção descoberta pelo ensaio de carga

O primeiro ensaio revelou que uma resposta de onda com 200 tarefas excedia os
65.535 bytes de `TEXT` no MySQL. V3 amplia `operacoes_wms.resultado` para
`MEDIUMTEXT`, mantendo o resultado completo para replay idempotente. A regressão
MySQL cria os 200 pedidos, confirma que a resposta excede o limite anterior e
repete a chave, verificando 200 tarefas/reservas e saldo físico inalterado.

O cálculo de reservas ativas passou a usar `SUM` no banco, preservando a subtração
do consumido/liberado e a exclusão de reservas encerradas. O lock por armazém
continua protegendo a consistência; as medições precisam registrar a espera das
outras operações. A mudança permanece na branch `homol` até revisão/merge.
