-- Teste de validação dos critérios de aceite (issue: carrinho / item_carrinho)

SET app.usuario_role = 'ADMIN';

DO $$
DECLARE
v_cliente_id    BIGINT;
    v_vendedor_id   BIGINT;
    v_categoria_id  BIGINT;
    v_produto_id    BIGINT;
    v_produto2_id   BIGINT;
    v_carrinho_id   BIGINT;
BEGIN
    -- Cliente de teste
INSERT INTO usuario (nome, email, senha_hash, role, dtype)
VALUES ('Cliente Teste Carrinho', 'teste.carrinho@exemplo.local', 'hash', 'CLIENTE', 'Cliente')
    RETURNING id INTO v_cliente_id;

-- Vendedor + categoria + 2 produtos de apoio (pra testar item_carrinho)
INSERT INTO usuario (nome, email, senha_hash, role, dtype)
VALUES ('Vendedor Teste Carrinho', 'vendedor.teste.carrinho@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
    RETURNING id INTO v_vendedor_id;

INSERT INTO vendedor (id, razao_social, cnpj_cpf)
VALUES (v_vendedor_id, 'Loja Teste Carrinho', '00000000000191');

INSERT INTO categoria (nome)
VALUES ('Categoria Teste Carrinho')
    RETURNING id INTO v_categoria_id;

INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
VALUES ('Produto Teste 1', 10, 50.00, v_vendedor_id, v_categoria_id)
    RETURNING id_produto INTO v_produto_id;

INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
VALUES ('Produto Teste 2', 10, 30.00, v_vendedor_id, v_categoria_id)
    RETURNING id_produto INTO v_produto2_id;

-- 1) INSERT válido em carrinho
INSERT INTO carrinho (id_cliente)
VALUES (v_cliente_id)
    RETURNING id INTO v_carrinho_id;
RAISE NOTICE 'OK: carrinho % inserido para o cliente %', v_carrinho_id, v_cliente_id;

    -- 2) Critério de aceite: NÃO pode inserir 2º carrinho pro mesmo cliente
BEGIN
INSERT INTO carrinho (id_cliente) VALUES (v_cliente_id);
RAISE EXCEPTION 'FALHA: foi possivel inserir um segundo carrinho para o mesmo cliente';
EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK: uq_carrinho_cliente bloqueou segundo carrinho para o mesmo cliente';
END;

    -- 3) INSERT válido em item_carrinho
INSERT INTO item_carrinho (carrinho_id, produto_id, quantidade, preco_unitario)
VALUES (v_carrinho_id, v_produto_id, 2, 50.00);
RAISE NOTICE 'OK: item inserido no carrinho % (produto %)', v_carrinho_id, v_produto_id;

    -- 4) Critério de aceite: NÃO pode inserir 2x o mesmo produto no mesmo carrinho
BEGIN
INSERT INTO item_carrinho (carrinho_id, produto_id, quantidade, preco_unitario)
VALUES (v_carrinho_id, v_produto_id, 1, 50.00);
RAISE EXCEPTION 'FALHA: foi possivel inserir o mesmo produto duas vezes no mesmo carrinho';
EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK: pk_item_carrinho bloqueou produto duplicado no mesmo carrinho';
END;

    -- 5) CHECK (quantidade > 0)
BEGIN
INSERT INTO item_carrinho (carrinho_id, produto_id, quantidade, preco_unitario)
VALUES (v_carrinho_id, v_produto2_id, 0, 30.00);
RAISE EXCEPTION 'FALHA: chk_item_carrinho_quantidade nao bloqueou quantidade <= 0';
EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK: chk_item_carrinho_quantidade bloqueou quantidade invalida';
END;

    -- 6) ON DELETE CASCADE: apagar o carrinho deve apagar os itens junto
DELETE FROM carrinho WHERE id = v_carrinho_id;

IF EXISTS (SELECT 1 FROM item_carrinho WHERE carrinho_id = v_carrinho_id) THEN
        RAISE EXCEPTION 'FALHA: itens do carrinho nao foram removidos em cascata';
ELSE
        RAISE NOTICE 'OK: ON DELETE CASCADE removeu os itens junto com o carrinho';
END IF;

    -- Limpeza final (produtos, categoria, vendedor, cliente de teste)
DELETE FROM produto  WHERE id_produto IN (v_produto_id, v_produto2_id);
DELETE FROM categoria WHERE id = v_categoria_id;
DELETE FROM usuario   WHERE id IN (v_cliente_id, v_vendedor_id);

RAISE NOTICE 'Teste de validacao de carrinho/item_carrinho concluido com sucesso';
END $$;

RESET app.usuario_role;