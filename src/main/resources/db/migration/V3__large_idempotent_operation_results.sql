-- A supported 200-order wave returns more than 64 KiB of task details.
-- Preserve its complete response so a retry can replay the original result.
ALTER TABLE operacoes_wms MODIFY COLUMN resultado MEDIUMTEXT;
