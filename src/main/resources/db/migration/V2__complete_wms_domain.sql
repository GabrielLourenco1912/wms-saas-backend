-- Initial WMS domain schema. This development baseline deliberately contains the complete domain.
-- Quantities use the product base unit; ledger/history rows are never deleted by cascade.

CREATE TABLE armazens (
    id VARCHAR(26) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    cnpj VARCHAR(14),
    endereco VARCHAR(255),
    criado_em DATETIME(6) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE zonas (
    id VARCHAR(26) NOT NULL,
    armazem_id VARCHAR(26) NOT NULL,
    nome VARCHAR(50) NOT NULL,
    tipo_controle VARCHAR(50),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_zonas_1 UNIQUE (armazem_id, nome)
);

CREATE TABLE posicoes (
    id VARCHAR(26) NOT NULL,
    zona_id VARCHAR(26) NOT NULL,
    codigo VARCHAR(50) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    capacidade_peso_kg DECIMAL(18,6),
    capacidade_volume_m3 DECIMAL(18,6),
    status VARCHAR(20) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    finalidade VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_posicoes_1 UNIQUE (zona_id, codigo),
    CONSTRAINT ck_posicoes_2 CHECK (capacidade_peso_kg IS NULL OR capacidade_peso_kg > 0),
    CONSTRAINT ck_posicoes_3 CHECK (capacidade_volume_m3 IS NULL OR capacidade_volume_m3 > 0)
);

CREATE TABLE categorias (
    id VARCHAR(26) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    descricao TEXT,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id)
);

CREATE TABLE produtos (
    id VARCHAR(26) NOT NULL,
    categoria_id VARCHAR(26) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    controle_lote BOOLEAN NOT NULL,
    controle_validade BOOLEAN NOT NULL,
    dias_shelf_life INT,
    criado_em DATETIME(6) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em DATETIME(6) NOT NULL,
    unidade_base VARCHAR(10) NOT NULL,
    peso_unitario_kg DECIMAL(18,6) NOT NULL,
    volume_unitario_m3 DECIMAL(18,6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_produtos_1 UNIQUE (sku),
    CONSTRAINT ck_produtos_2 CHECK (peso_unitario_kg >= 0),
    CONSTRAINT ck_produtos_3 CHECK (volume_unitario_m3 >= 0),
    CONSTRAINT ck_produtos_4 CHECK (dias_shelf_life IS NULL OR dias_shelf_life >= 0)
);

CREATE TABLE produto_embalagens (
    id VARCHAR(26) NOT NULL,
    produto_id VARCHAR(26) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    fator_conversao DECIMAL(18,6) NOT NULL,
    codigo_barras VARCHAR(100),
    peso_kg DECIMAL(18,6),
    volume_m3 DECIMAL(18,6),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_produto_embalagens_1 UNIQUE (codigo_barras),
    CONSTRAINT ck_produto_embalagens_2 CHECK (fator_conversao > 0),
    CONSTRAINT ck_produto_embalagens_3 CHECK (peso_kg IS NULL OR peso_kg >= 0),
    CONSTRAINT ck_produto_embalagens_4 CHECK (volume_m3 IS NULL OR volume_m3 >= 0)
);

CREATE TABLE terceiros (
    id VARCHAR(26) NOT NULL,
    razao_social VARCHAR(150) NOT NULL,
    cpf_cnpj VARCHAR(14),
    tipo VARCHAR(30) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em DATETIME(6) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE pedidos (
    id VARCHAR(26) NOT NULL,
    terceiro_id VARCHAR(26) NOT NULL,
    numero_documento VARCHAR(50),
    tipo VARCHAR(20) NOT NULL,
    data_prevista DATETIME(6),
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    armazem_id VARCHAR(26) NOT NULL,
    criado_por_id VARCHAR(255) NOT NULL,
    cancelado_em DATETIME(6),
    cancelado_por_id VARCHAR(255),
    motivo_cancelamento VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE TABLE itens_pedido (
    id VARCHAR(26) NOT NULL,
    pedido_id VARCHAR(26) NOT NULL,
    produto_id VARCHAR(26) NOT NULL,
    quantidade_solicitada DECIMAL(18,6) NOT NULL,
    quantidade_atendida DECIMAL(18,6) NOT NULL DEFAULT 0,
    quantidade_separada DECIMAL(18,6) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_itens_pedido_1 UNIQUE (pedido_id, produto_id),
    CONSTRAINT ck_itens_pedido_2 CHECK (quantidade_solicitada > 0),
    CONSTRAINT ck_itens_pedido_3 CHECK (quantidade_atendida >= 0 AND quantidade_separada >= 0 AND quantidade_atendida + quantidade_separada <= quantidade_solicitada)
);

CREATE TABLE lpns (
    id VARCHAR(26) NOT NULL,
    codigo_rastreio VARCHAR(100) NOT NULL,
    posicao_id VARCHAR(26),
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    pedido_saida_id VARCHAR(26),
    PRIMARY KEY (id),
    CONSTRAINT uk_lpns_1 UNIQUE (codigo_rastreio)
);

CREATE TABLE estoque (
    id VARCHAR(26) NOT NULL,
    produto_id VARCHAR(26) NOT NULL,
    lpn_id VARCHAR(26),
    posicao_id VARCHAR(26) NOT NULL,
    lote VARCHAR(100),
    data_validade DATE,
    quantidade DECIMAL(18,6) NOT NULL,
    status_bloqueio VARCHAR(30),
    atualizado_em DATETIME(6) NOT NULL,
    data_entrada_em DATETIME(6) NOT NULL,
    conferencia_origem_id VARCHAR(26),
    versao BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_estoque_1 CHECK (quantidade >= 0)
);

CREATE TABLE ondas_separacao (
    id VARCHAR(26) NOT NULL,
    codigo_onda VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    atualizado_em DATETIME(6) NOT NULL,
    armazem_id VARCHAR(26) NOT NULL,
    criado_por_id VARCHAR(255) NOT NULL,
    cancelado_em DATETIME(6),
    cancelado_por_id VARCHAR(255),
    motivo_cancelamento VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_ondas_separacao_1 UNIQUE (codigo_onda)
);

CREATE TABLE onda_pedidos (
    onda_id VARCHAR(26) NOT NULL,
    pedido_id VARCHAR(26) NOT NULL,
    PRIMARY KEY (onda_id, pedido_id)
);

CREATE TABLE tarefas (
    id VARCHAR(26) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    prioridade INT,
    onda_id VARCHAR(26),
    pedido_id VARCHAR(26),
    usuario_atribuido_id VARCHAR(255),
    posicao_origem_id VARCHAR(26),
    posicao_destino_id VARCHAR(26),
    lpn_id VARCHAR(26),
    produto_id VARCHAR(26),
    quantidade_alvo DECIMAL(18,6) NOT NULL,
    quantidade_realizada DECIMAL(18,6),
    status VARCHAR(20) NOT NULL,
    item_pedido_id VARCHAR(26),
    estoque_origem_id VARCHAR(26),
    lpn_destino_id VARCHAR(26),
    tarefa_origem_id VARCHAR(26),
    criado_em DATETIME(6) NOT NULL,
    iniciado_em DATETIME(6),
    concluido_em DATETIME(6),
    cancelado_em DATETIME(6),
    cancelado_por_id VARCHAR(255),
    motivo_cancelamento VARCHAR(255),
    versao BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE reservas_estoque (
    id VARCHAR(26) NOT NULL,
    estoque_id VARCHAR(26) NOT NULL,
    tarefa_id VARCHAR(26) NOT NULL,
    item_pedido_id VARCHAR(26) NOT NULL,
    quantidade_reservada DECIMAL(18,6) NOT NULL,
    quantidade_consumida DECIMAL(18,6) NOT NULL DEFAULT 0,
    quantidade_liberada DECIMAL(18,6) NOT NULL DEFAULT 0,
    status VARCHAR(15) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    encerrado_em DATETIME(6),
    motivo_encerramento VARCHAR(255),
    PRIMARY KEY (id),
    CONSTRAINT uk_reservas_estoque_1 UNIQUE (tarefa_id),
    CONSTRAINT ck_reservas_estoque_2 CHECK (quantidade_reservada > 0 AND quantidade_consumida >= 0 AND quantidade_liberada >= 0 AND quantidade_consumida + quantidade_liberada <= quantidade_reservada)
);

CREATE TABLE conferencias_recebimento (
    id VARCHAR(26) NOT NULL,
    item_pedido_id VARCHAR(26) NOT NULL,
    embalagem_id VARCHAR(26) NOT NULL,
    lpn_id VARCHAR(26) NOT NULL,
    posicao_id VARCHAR(26) NOT NULL,
    quantidade_informada DECIMAL(18,6) NOT NULL,
    quantidade_base DECIMAL(18,6) NOT NULL,
    lote VARCHAR(100),
    data_validade DATE,
    usuario_id VARCHAR(255) NOT NULL,
    operacao_id VARCHAR(26) NOT NULL,
    conferido_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_conferencias_recebimento_1 CHECK (quantidade_informada > 0 AND quantidade_base > 0)
);

CREATE TABLE historico_tarefas (
    id VARCHAR(26) NOT NULL,
    tarefa_id VARCHAR(26) NOT NULL,
    status_anterior VARCHAR(20),
    status_novo VARCHAR(20) NOT NULL,
    usuario_id VARCHAR(255),
    operacao_id VARCHAR(26) NOT NULL,
    motivo VARCHAR(255),
    registrado_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE divergencias_operacionais (
    id VARCHAR(26) NOT NULL,
    tarefa_id VARCHAR(26) NOT NULL,
    estoque_id VARCHAR(26),
    posicao_id VARCHAR(26) NOT NULL,
    usuario_id VARCHAR(255) NOT NULL,
    quantidade_prevista DECIMAL(18,6) NOT NULL,
    quantidade_realizada DECIMAL(18,6) NOT NULL,
    quantidade_faltante DECIMAL(18,6) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    resolvido_em DATETIME(6),
    autorizado_por_id VARCHAR(255),
    autorizado_em DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_divergencias_operacionais_1 CHECK (quantidade_prevista >= 0 AND quantidade_realizada >= 0 AND quantidade_faltante >= 0)
);

CREATE TABLE bloqueios_estoque (
    id VARCHAR(26) NOT NULL,
    armazem_id VARCHAR(26) NOT NULL,
    tipo VARCHAR(15) NOT NULL,
    estoque_id VARCHAR(26),
    produto_id VARCHAR(26),
    lote VARCHAR(100),
    posicao_id VARCHAR(26),
    motivo VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por_id VARCHAR(255) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    liberado_por_id VARCHAR(255),
    liberado_em DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_bloqueios_estoque_1 CHECK ((tipo = 'ESTOQUE' AND estoque_id IS NOT NULL AND produto_id IS NULL AND lote IS NULL AND posicao_id IS NULL) OR (tipo = 'LOTE' AND estoque_id IS NULL AND produto_id IS NOT NULL AND lote IS NOT NULL AND posicao_id IS NULL) OR (tipo = 'POSICAO' AND estoque_id IS NULL AND produto_id IS NULL AND lote IS NULL AND posicao_id IS NOT NULL))
);

CREATE TABLE inventarios (
    id VARCHAR(26) NOT NULL,
    armazem_id VARCHAR(26) NOT NULL,
    posicao_id VARCHAR(26) NOT NULL,
    usuario_responsavel_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    concluido_em DATETIME(6),
    cancelado_em DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE itens_inventario (
    id VARCHAR(26) NOT NULL,
    inventario_id VARCHAR(26) NOT NULL,
    estoque_id VARCHAR(26),
    produto_id VARCHAR(26) NOT NULL,
    lpn_id VARCHAR(26) NOT NULL,
    lote VARCHAR(100),
    data_validade DATE,
    quantidade_teorica DECIMAL(18,6) NOT NULL,
    quantidade_contada DECIMAL(18,6),
    usuario_contagem_id VARCHAR(255),
    contado_em DATETIME(6),
    aplicado_em DATETIME(6),
    operacao_aplicacao_id VARCHAR(26),
    PRIMARY KEY (id),
    CONSTRAINT ck_itens_inventario_1 CHECK (quantidade_teorica >= 0),
    CONSTRAINT ck_itens_inventario_2 CHECK (quantidade_contada IS NULL OR quantidade_contada >= 0)
);

CREATE TABLE expedicoes (
    id VARCHAR(26) NOT NULL,
    pedido_id VARCHAR(26) NOT NULL,
    posicao_doca_id VARCHAR(26) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_por_id VARCHAR(255) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    despachado_por_id VARCHAR(255),
    despachado_em DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE itens_expedicao (
    id VARCHAR(26) NOT NULL,
    expedicao_id VARCHAR(26) NOT NULL,
    item_pedido_id VARCHAR(26) NOT NULL,
    estoque_id VARCHAR(26) NOT NULL,
    lpn_id VARCHAR(26) NOT NULL,
    quantidade DECIMAL(18,6) NOT NULL,
    status VARCHAR(20) NOT NULL,
    conferido_por_id VARCHAR(255),
    conferido_em DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_itens_expedicao_1 UNIQUE (expedicao_id, estoque_id),
    CONSTRAINT ck_itens_expedicao_2 CHECK (quantidade > 0)
);

CREATE TABLE operacoes_wms (
    id VARCHAR(26) NOT NULL,
    chave_idempotencia VARCHAR(64) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    usuario_id VARCHAR(255),
    hash_requisicao VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_em DATETIME(6) NOT NULL,
    concluido_em DATETIME(6),
    resultado TEXT,
    PRIMARY KEY (id),
    CONSTRAINT uk_operacoes_wms_1 UNIQUE (chave_idempotencia)
);

CREATE TABLE transacoes_estoque (
    id VARCHAR(26) NOT NULL,
    produto_id VARCHAR(26) NOT NULL,
    usuario_id VARCHAR(255) NOT NULL,
    tarefa_id VARCHAR(26),
    lpn_id VARCHAR(26),
    tipo_movimento VARCHAR(50) NOT NULL,
    quantidade DECIMAL(18,6) NOT NULL,
    posicao_anterior_id VARCHAR(26),
    posicao_nova_id VARCHAR(26),
    saldo_resultante DECIMAL(18,6) NOT NULL,
    data_hora DATETIME(6) NOT NULL,
    operacao_id VARCHAR(26) NOT NULL,
    sequencia INT NOT NULL,
    estoque_id VARCHAR(26) NOT NULL,
    estoque_destino_id VARCHAR(26),
    lpn_destino_id VARCHAR(26),
    lote_registrado VARCHAR(100),
    validade_registrada DATE,
    entrada_registrada_em DATETIME(6) NOT NULL,
    saldo_anterior DECIMAL(18,6) NOT NULL,
    saldo_destino_anterior DECIMAL(18,6),
    saldo_destino_resultante DECIMAL(18,6),
    transacao_compensada_id VARCHAR(26),
    motivo VARCHAR(255),
    inventario_item_id VARCHAR(26),
    expedicao_item_id VARCHAR(26),
    PRIMARY KEY (id),
    CONSTRAINT uk_transacoes_estoque_1 UNIQUE (operacao_id, sequencia),
    CONSTRAINT ck_transacoes_estoque_2 CHECK (saldo_anterior >= 0 AND saldo_resultante >= 0),
    CONSTRAINT ck_transacoes_estoque_3 CHECK (saldo_destino_anterior IS NULL OR saldo_destino_anterior >= 0),
    CONSTRAINT ck_transacoes_estoque_4 CHECK (saldo_destino_resultante IS NULL OR saldo_destino_resultante >= 0)
);

-- Add references after all tables exist, including the stock/receipt and self-referencing audit links.
ALTER TABLE zonas ADD CONSTRAINT fk_zonas_armazem_id FOREIGN KEY (armazem_id) REFERENCES armazens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE posicoes ADD CONSTRAINT fk_posicoes_zona_id FOREIGN KEY (zona_id) REFERENCES zonas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE produtos ADD CONSTRAINT fk_produtos_categoria_id FOREIGN KEY (categoria_id) REFERENCES categorias (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE produto_embalagens ADD CONSTRAINT fk_produto_embalagens_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_terceiro_id FOREIGN KEY (terceiro_id) REFERENCES terceiros (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_armazem_id FOREIGN KEY (armazem_id) REFERENCES armazens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_criado_por_id FOREIGN KEY (criado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_cancelado_por_id FOREIGN KEY (cancelado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_pedido ADD CONSTRAINT fk_itens_pedido_pedido_id FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_pedido ADD CONSTRAINT fk_itens_pedido_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE lpns ADD CONSTRAINT fk_lpns_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE lpns ADD CONSTRAINT fk_lpns_pedido_saida_id FOREIGN KEY (pedido_saida_id) REFERENCES pedidos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE estoque ADD CONSTRAINT fk_estoque_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE estoque ADD CONSTRAINT fk_estoque_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE estoque ADD CONSTRAINT fk_estoque_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE estoque ADD CONSTRAINT fk_estoque_conferencia_origem_id FOREIGN KEY (conferencia_origem_id) REFERENCES conferencias_recebimento (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ondas_separacao ADD CONSTRAINT fk_ondas_separacao_armazem_id FOREIGN KEY (armazem_id) REFERENCES armazens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ondas_separacao ADD CONSTRAINT fk_ondas_separacao_criado_por_id FOREIGN KEY (criado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ondas_separacao ADD CONSTRAINT fk_ondas_separacao_cancelado_por_id FOREIGN KEY (cancelado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE onda_pedidos ADD CONSTRAINT fk_onda_pedidos_onda_id FOREIGN KEY (onda_id) REFERENCES ondas_separacao (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE onda_pedidos ADD CONSTRAINT fk_onda_pedidos_pedido_id FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_onda_id FOREIGN KEY (onda_id) REFERENCES ondas_separacao (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_pedido_id FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_usuario_atribuido_id FOREIGN KEY (usuario_atribuido_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_posicao_origem_id FOREIGN KEY (posicao_origem_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_posicao_destino_id FOREIGN KEY (posicao_destino_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_item_pedido_id FOREIGN KEY (item_pedido_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_estoque_origem_id FOREIGN KEY (estoque_origem_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_lpn_destino_id FOREIGN KEY (lpn_destino_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_tarefa_origem_id FOREIGN KEY (tarefa_origem_id) REFERENCES tarefas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE tarefas ADD CONSTRAINT fk_tarefas_cancelado_por_id FOREIGN KEY (cancelado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE reservas_estoque ADD CONSTRAINT fk_reservas_estoque_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE reservas_estoque ADD CONSTRAINT fk_reservas_estoque_tarefa_id FOREIGN KEY (tarefa_id) REFERENCES tarefas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE reservas_estoque ADD CONSTRAINT fk_reservas_estoque_item_pedido_id FOREIGN KEY (item_pedido_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_item_pedido_id FOREIGN KEY (item_pedido_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_embalagem_id FOREIGN KEY (embalagem_id) REFERENCES produto_embalagens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_usuario_id FOREIGN KEY (usuario_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE conferencias_recebimento ADD CONSTRAINT fk_conferencias_recebimento_operacao_id FOREIGN KEY (operacao_id) REFERENCES operacoes_wms (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE historico_tarefas ADD CONSTRAINT fk_historico_tarefas_tarefa_id FOREIGN KEY (tarefa_id) REFERENCES tarefas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE historico_tarefas ADD CONSTRAINT fk_historico_tarefas_usuario_id FOREIGN KEY (usuario_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE historico_tarefas ADD CONSTRAINT fk_historico_tarefas_operacao_id FOREIGN KEY (operacao_id) REFERENCES operacoes_wms (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE divergencias_operacionais ADD CONSTRAINT fk_divergencias_operacionais_tarefa_id FOREIGN KEY (tarefa_id) REFERENCES tarefas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE divergencias_operacionais ADD CONSTRAINT fk_divergencias_operacionais_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE divergencias_operacionais ADD CONSTRAINT fk_divergencias_operacionais_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE divergencias_operacionais ADD CONSTRAINT fk_divergencias_operacionais_usuario_id FOREIGN KEY (usuario_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE divergencias_operacionais ADD CONSTRAINT fk_divergencias_operacionais_autorizado_por_id FOREIGN KEY (autorizado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_armazem_id FOREIGN KEY (armazem_id) REFERENCES armazens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_criado_por_id FOREIGN KEY (criado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE bloqueios_estoque ADD CONSTRAINT fk_bloqueios_estoque_liberado_por_id FOREIGN KEY (liberado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE inventarios ADD CONSTRAINT fk_inventarios_armazem_id FOREIGN KEY (armazem_id) REFERENCES armazens (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE inventarios ADD CONSTRAINT fk_inventarios_posicao_id FOREIGN KEY (posicao_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE inventarios ADD CONSTRAINT fk_inventarios_usuario_responsavel_id FOREIGN KEY (usuario_responsavel_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_inventario_id FOREIGN KEY (inventario_id) REFERENCES inventarios (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_usuario_contagem_id FOREIGN KEY (usuario_contagem_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_inventario ADD CONSTRAINT fk_itens_inventario_operacao_aplicacao_id FOREIGN KEY (operacao_aplicacao_id) REFERENCES operacoes_wms (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE expedicoes ADD CONSTRAINT fk_expedicoes_pedido_id FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE expedicoes ADD CONSTRAINT fk_expedicoes_posicao_doca_id FOREIGN KEY (posicao_doca_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE expedicoes ADD CONSTRAINT fk_expedicoes_criado_por_id FOREIGN KEY (criado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE expedicoes ADD CONSTRAINT fk_expedicoes_despachado_por_id FOREIGN KEY (despachado_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_expedicao ADD CONSTRAINT fk_itens_expedicao_expedicao_id FOREIGN KEY (expedicao_id) REFERENCES expedicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_expedicao ADD CONSTRAINT fk_itens_expedicao_item_pedido_id FOREIGN KEY (item_pedido_id) REFERENCES itens_pedido (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_expedicao ADD CONSTRAINT fk_itens_expedicao_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_expedicao ADD CONSTRAINT fk_itens_expedicao_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE itens_expedicao ADD CONSTRAINT fk_itens_expedicao_conferido_por_id FOREIGN KEY (conferido_por_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE operacoes_wms ADD CONSTRAINT fk_operacoes_wms_usuario_id FOREIGN KEY (usuario_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_produto_id FOREIGN KEY (produto_id) REFERENCES produtos (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_usuario_id FOREIGN KEY (usuario_id) REFERENCES users (ulid) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_tarefa_id FOREIGN KEY (tarefa_id) REFERENCES tarefas (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_lpn_id FOREIGN KEY (lpn_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_posicao_anterior_id FOREIGN KEY (posicao_anterior_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_posicao_nova_id FOREIGN KEY (posicao_nova_id) REFERENCES posicoes (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_operacao_id FOREIGN KEY (operacao_id) REFERENCES operacoes_wms (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_estoque_id FOREIGN KEY (estoque_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_estoque_destino_id FOREIGN KEY (estoque_destino_id) REFERENCES estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_lpn_destino_id FOREIGN KEY (lpn_destino_id) REFERENCES lpns (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_transacao_compensada_id FOREIGN KEY (transacao_compensada_id) REFERENCES transacoes_estoque (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_inventario_item_id FOREIGN KEY (inventario_item_id) REFERENCES itens_inventario (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE transacoes_estoque ADD CONSTRAINT fk_transacoes_estoque_expedicao_item_id FOREIGN KEY (expedicao_item_id) REFERENCES itens_expedicao (id) ON DELETE RESTRICT ON UPDATE RESTRICT;

CREATE INDEX ix_zonas_armazem_id ON zonas (armazem_id);
CREATE INDEX ix_posicoes_zona_id ON posicoes (zona_id);
CREATE INDEX ix_produtos_categoria_id ON produtos (categoria_id);
CREATE INDEX ix_produto_embalagens_produto_id ON produto_embalagens (produto_id);
CREATE INDEX ix_pedidos_terceiro_id ON pedidos (terceiro_id);
CREATE INDEX ix_pedidos_armazem_id ON pedidos (armazem_id);
CREATE INDEX ix_pedidos_criado_por_id ON pedidos (criado_por_id);
CREATE INDEX ix_pedidos_cancelado_por_id ON pedidos (cancelado_por_id);
CREATE INDEX ix_itens_pedido_pedido_id ON itens_pedido (pedido_id);
CREATE INDEX ix_itens_pedido_produto_id ON itens_pedido (produto_id);
CREATE INDEX ix_lpns_posicao_id ON lpns (posicao_id);
CREATE INDEX ix_lpns_pedido_saida_id ON lpns (pedido_saida_id);
CREATE INDEX ix_estoque_produto_id ON estoque (produto_id);
CREATE INDEX ix_estoque_lpn_id ON estoque (lpn_id);
CREATE INDEX ix_estoque_posicao_id ON estoque (posicao_id);
CREATE INDEX ix_estoque_conferencia_origem_id ON estoque (conferencia_origem_id);
CREATE INDEX ix_ondas_separacao_armazem_id ON ondas_separacao (armazem_id);
CREATE INDEX ix_ondas_separacao_criado_por_id ON ondas_separacao (criado_por_id);
CREATE INDEX ix_ondas_separacao_cancelado_por_id ON ondas_separacao (cancelado_por_id);
CREATE INDEX ix_onda_pedidos_onda_id ON onda_pedidos (onda_id);
CREATE INDEX ix_onda_pedidos_pedido_id ON onda_pedidos (pedido_id);
CREATE INDEX ix_tarefas_onda_id ON tarefas (onda_id);
CREATE INDEX ix_tarefas_pedido_id ON tarefas (pedido_id);
CREATE INDEX ix_tarefas_usuario_atribuido_id ON tarefas (usuario_atribuido_id);
CREATE INDEX ix_tarefas_posicao_origem_id ON tarefas (posicao_origem_id);
CREATE INDEX ix_tarefas_posicao_destino_id ON tarefas (posicao_destino_id);
CREATE INDEX ix_tarefas_lpn_id ON tarefas (lpn_id);
CREATE INDEX ix_tarefas_produto_id ON tarefas (produto_id);
CREATE INDEX ix_tarefas_item_pedido_id ON tarefas (item_pedido_id);
CREATE INDEX ix_tarefas_estoque_origem_id ON tarefas (estoque_origem_id);
CREATE INDEX ix_tarefas_lpn_destino_id ON tarefas (lpn_destino_id);
CREATE INDEX ix_tarefas_tarefa_origem_id ON tarefas (tarefa_origem_id);
CREATE INDEX ix_tarefas_cancelado_por_id ON tarefas (cancelado_por_id);
CREATE INDEX ix_reservas_estoque_estoque_id ON reservas_estoque (estoque_id);
CREATE INDEX ix_reservas_estoque_tarefa_id ON reservas_estoque (tarefa_id);
CREATE INDEX ix_reservas_estoque_item_pedido_id ON reservas_estoque (item_pedido_id);
CREATE INDEX ix_conferencias_recebimento_item_pedido_id ON conferencias_recebimento (item_pedido_id);
CREATE INDEX ix_conferencias_recebimento_embalagem_id ON conferencias_recebimento (embalagem_id);
CREATE INDEX ix_conferencias_recebimento_lpn_id ON conferencias_recebimento (lpn_id);
CREATE INDEX ix_conferencias_recebimento_posicao_id ON conferencias_recebimento (posicao_id);
CREATE INDEX ix_conferencias_recebimento_usuario_id ON conferencias_recebimento (usuario_id);
CREATE INDEX ix_conferencias_recebimento_operacao_id ON conferencias_recebimento (operacao_id);
CREATE INDEX ix_historico_tarefas_tarefa_id ON historico_tarefas (tarefa_id);
CREATE INDEX ix_historico_tarefas_usuario_id ON historico_tarefas (usuario_id);
CREATE INDEX ix_historico_tarefas_operacao_id ON historico_tarefas (operacao_id);
CREATE INDEX ix_divergencias_operacionais_tarefa_id ON divergencias_operacionais (tarefa_id);
CREATE INDEX ix_divergencias_operacionais_estoque_id ON divergencias_operacionais (estoque_id);
CREATE INDEX ix_divergencias_operacionais_posicao_id ON divergencias_operacionais (posicao_id);
CREATE INDEX ix_divergencias_operacionais_usuario_id ON divergencias_operacionais (usuario_id);
CREATE INDEX ix_divergencias_operacionais_autorizado_por_id ON divergencias_operacionais (autorizado_por_id);
CREATE INDEX ix_bloqueios_estoque_armazem_id ON bloqueios_estoque (armazem_id);
CREATE INDEX ix_bloqueios_estoque_estoque_id ON bloqueios_estoque (estoque_id);
CREATE INDEX ix_bloqueios_estoque_produto_id ON bloqueios_estoque (produto_id);
CREATE INDEX ix_bloqueios_estoque_posicao_id ON bloqueios_estoque (posicao_id);
CREATE INDEX ix_bloqueios_estoque_criado_por_id ON bloqueios_estoque (criado_por_id);
CREATE INDEX ix_bloqueios_estoque_liberado_por_id ON bloqueios_estoque (liberado_por_id);
CREATE INDEX ix_inventarios_armazem_id ON inventarios (armazem_id);
CREATE INDEX ix_inventarios_posicao_id ON inventarios (posicao_id);
CREATE INDEX ix_inventarios_usuario_responsavel_id ON inventarios (usuario_responsavel_id);
CREATE INDEX ix_itens_inventario_inventario_id ON itens_inventario (inventario_id);
CREATE INDEX ix_itens_inventario_estoque_id ON itens_inventario (estoque_id);
CREATE INDEX ix_itens_inventario_produto_id ON itens_inventario (produto_id);
CREATE INDEX ix_itens_inventario_lpn_id ON itens_inventario (lpn_id);
CREATE INDEX ix_itens_inventario_usuario_contagem_id ON itens_inventario (usuario_contagem_id);
CREATE INDEX ix_itens_inventario_operacao_aplicacao_id ON itens_inventario (operacao_aplicacao_id);
CREATE INDEX ix_expedicoes_pedido_id ON expedicoes (pedido_id);
CREATE INDEX ix_expedicoes_posicao_doca_id ON expedicoes (posicao_doca_id);
CREATE INDEX ix_expedicoes_criado_por_id ON expedicoes (criado_por_id);
CREATE INDEX ix_expedicoes_despachado_por_id ON expedicoes (despachado_por_id);
CREATE INDEX ix_itens_expedicao_expedicao_id ON itens_expedicao (expedicao_id);
CREATE INDEX ix_itens_expedicao_item_pedido_id ON itens_expedicao (item_pedido_id);
CREATE INDEX ix_itens_expedicao_estoque_id ON itens_expedicao (estoque_id);
CREATE INDEX ix_itens_expedicao_lpn_id ON itens_expedicao (lpn_id);
CREATE INDEX ix_itens_expedicao_conferido_por_id ON itens_expedicao (conferido_por_id);
CREATE INDEX ix_operacoes_wms_usuario_id ON operacoes_wms (usuario_id);
CREATE INDEX ix_transacoes_estoque_produto_id ON transacoes_estoque (produto_id);
CREATE INDEX ix_transacoes_estoque_usuario_id ON transacoes_estoque (usuario_id);
CREATE INDEX ix_transacoes_estoque_tarefa_id ON transacoes_estoque (tarefa_id);
CREATE INDEX ix_transacoes_estoque_lpn_id ON transacoes_estoque (lpn_id);
CREATE INDEX ix_transacoes_estoque_posicao_anterior_id ON transacoes_estoque (posicao_anterior_id);
CREATE INDEX ix_transacoes_estoque_posicao_nova_id ON transacoes_estoque (posicao_nova_id);
CREATE INDEX ix_transacoes_estoque_operacao_id ON transacoes_estoque (operacao_id);
CREATE INDEX ix_transacoes_estoque_estoque_id ON transacoes_estoque (estoque_id);
CREATE INDEX ix_transacoes_estoque_estoque_destino_id ON transacoes_estoque (estoque_destino_id);
CREATE INDEX ix_transacoes_estoque_lpn_destino_id ON transacoes_estoque (lpn_destino_id);
CREATE INDEX ix_transacoes_estoque_transacao_compensada_id ON transacoes_estoque (transacao_compensada_id);
CREATE INDEX ix_transacoes_estoque_inventario_item_id ON transacoes_estoque (inventario_item_id);
CREATE INDEX ix_transacoes_estoque_expedicao_item_id ON transacoes_estoque (expedicao_item_id);

CREATE INDEX ix_estoque_allocation ON estoque (produto_id, status_bloqueio, data_validade, data_entrada_em, id);
CREATE INDEX ix_reservas_estoque_available ON reservas_estoque (estoque_id, status);
CREATE INDEX ix_tarefas_work_queue ON tarefas (status, tipo, prioridade, criado_em);
CREATE INDEX ix_transacoes_estoque_time ON transacoes_estoque (data_hora, id);
CREATE INDEX ix_bloqueios_estoque_active ON bloqueios_estoque (armazem_id, ativo, tipo);
CREATE INDEX ix_inventarios_position_status ON inventarios (posicao_id, status);
