-- Script de GRANT para o role app_ecommerce
-- Executar conforme as tabelas forem criadas

-- Permissões nas tabelas (aplicar após cada CREATE TABLE)
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE nome_da_tabela TO app_ecommerce;

-- Permissões nas sequences (aplicar após cada CREATE SEQUENCE ou SERIAL)
-- GRANT USAGE, SELECT ON SEQUENCE nome_da_sequence TO app_ecommerce;

-- Exemplo (quando a tabela usuario existir):
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE usuario TO app_ecommerce;
-- GRANT USAGE, SELECT ON SEQUENCE usuario_id_seq TO app_ecommerce;