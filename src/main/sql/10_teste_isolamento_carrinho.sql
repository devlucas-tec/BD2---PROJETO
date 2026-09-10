-- ============================================================
-- 10_teste_isolamento_carrinho.sql
-- Testa o critério de aceite da issue #12:
-- "SELECT * FROM item_carrinho como cliente A retorna apenas
--  itens do carrinho dele, mesmo sem WHERE na query."
-- ============================================================

-- Ajuste os IDs de cliente/carrinho conforme os dados que você
-- já tem em 08_teste_carrinho_item_carrinho.sql

BEGIN;

-- Simula o cliente A (troque 1 pelo id_cliente real do cliente A)
SET ROLE app_ecommerce;
SET LOCAL app.usuario_id = '1';
SET LOCAL app.usuario_role = 'CLIENTE';

-- Sem WHERE nenhum -> deve trazer só os itens do carrinho do cliente A
SELECT * FROM item_carrinho;
SELECT * FROM carrinho;

ROLLBACK;


BEGIN;

-- Simula o cliente B (troque 2 pelo id_cliente real do cliente B)
SET ROLE app_ecommerce;
SET LOCAL app.usuario_id = '2';
SET LOCAL app.usuario_role = 'CLIENTE';

-- Deve trazer só os itens do carrinho do cliente B
SELECT * FROM item_carrinho;
SELECT * FROM carrinho;

-- Tenta enxergar/alterar o carrinho do cliente A -> deve retornar 0 linhas
-- e o UPDATE não deve afetar nada
SELECT * FROM carrinho WHERE id_cliente = 1;
UPDATE carrinho SET id_cliente = 2 WHERE id_cliente = 1; -- não deve afetar linhas

ROLLBACK;


BEGIN;

-- Testa que o cliente B não consegue "roubar" um item movendo-o
-- para um carrinho que não é dele (ajuste os ids de item e carrinho)
SET ROLE app_ecommerce;
SET LOCAL app.usuario_id = '2';
SET LOCAL app.usuario_role = 'CLIENTE';

-- Supondo que o item X pertence ao carrinho do cliente B,
-- tentar trocar carrinho_id para um carrinho do cliente A deve falhar
-- (0 linhas afetadas, por causa do WITH CHECK)
UPDATE item_carrinho
SET carrinho_id = (SELECT id FROM carrinho WHERE id_cliente = 1 LIMIT 1)
WHERE carrinho_id = (SELECT id FROM carrinho WHERE id_cliente = 2 LIMIT 1);

ROLLBACK;