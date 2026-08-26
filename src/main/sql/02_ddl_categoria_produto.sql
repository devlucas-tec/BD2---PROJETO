-- ============================================================
-- Issue #6: DDL das tabelas categoria e produto
-- Pré-requisito: 01_grants_app_ecommerce.sql (usuario/vendedor + role app_ecommerce)
-- ============================================================

-- 1. Sequences
CREATE SEQUENCE seq_categoria_id;
CREATE SEQUENCE seq_produto_id;

-- 2. Tabela categoria
CREATE TABLE categoria (
    id        BIGINT       NOT NULL DEFAULT nextval('seq_categoria_id'),
    nome      VARCHAR(120) NOT NULL UNIQUE,
    descricao VARCHAR(255),
    CONSTRAINT pk_categoria PRIMARY KEY (id)
);

-- 3. Tabela produto
CREATE TABLE produto (
    id_produto       BIGINT        NOT NULL DEFAULT nextval('seq_produto_id'),
    nome             VARCHAR(255)  NOT NULL,
    descricao        TEXT,
    estoque          INTEGER       NOT NULL DEFAULT 0,
    preco            NUMERIC(10,2) NOT NULL,
    id_vendedor      BIGINT        NOT NULL,
    id_categoria     BIGINT        NOT NULL,
    data_cadastro    TIMESTAMP     NOT NULL DEFAULT now(),
    data_atualizacao TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT pk_produto PRIMARY KEY (id_produto),
    CONSTRAINT fk_produto_vendedor FOREIGN KEY (id_vendedor)
        REFERENCES vendedor (id) ON DELETE CASCADE,
    CONSTRAINT fk_produto_categoria FOREIGN KEY (id_categoria)
        REFERENCES categoria (id) ON DELETE RESTRICT,
    CONSTRAINT chk_produto_estoque CHECK (estoque >= 0),
    CONSTRAINT chk_produto_preco   CHECK (preco >= 0)
);

-- 4. Índices das FKs
-- (categoria.nome já possui índice implícito criado pela constraint UNIQUE)
CREATE INDEX idx_produto_vendedor  ON produto (id_vendedor);
CREATE INDEX idx_produto_categoria ON produto (id_categoria);

-- 5. Grants
GRANT SELECT, INSERT, UPDATE, DELETE
    ON categoria, produto
    TO app_ecommerce;

GRANT USAGE, SELECT
    ON SEQUENCE seq_categoria_id, seq_produto_id
    TO app_ecommerce;

-- ============================================================
-- 6. Teste de validação (critério de aceite da issue #6)
--    Insere um cenário completo, valida constraints/FKs e
--    remove tudo que criou ao final. Não deixa resíduo no banco.
-- ============================================================

-- usuario/vendedor estão sob FORCE ROW LEVEL SECURITY (01_grants_app_ecommerce.sql).
-- Sem um contexto de tenant, o INSERT ... RETURNING seria filtrado pela policy de
-- SELECT e o teste receberia NULL. Assumimos o papel de ADMIN durante o teste.
SET app.usuario_role = 'ADMIN';

DO $$
DECLARE
    v_usuario_id   BIGINT;
    v_categoria_id BIGINT;
    v_produto_id   BIGINT;
BEGIN
    -- Vendedor de apoio (usuario + vendedor)
    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Vendedor Teste #6', 'teste.issue6@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
      RETURNING id INTO v_usuario_id;

    INSERT INTO vendedor (id, razao_social, cnpj_cpf)
         VALUES (v_usuario_id, 'Loja Teste LTDA', '00000000000191');

    -- INSERT válido em categoria
    INSERT INTO categoria (nome, descricao)
         VALUES ('Eletronicos Teste #6', 'Categoria usada apenas no teste de validacao')
      RETURNING id INTO v_categoria_id;

    -- INSERT válido em produto (valida as duas FKs)
    INSERT INTO produto (nome, descricao, estoque, preco, id_vendedor, id_categoria)
         VALUES ('Teclado Mecanico', 'Produto de teste', 10, 249.90, v_usuario_id, v_categoria_id)
      RETURNING id_produto INTO v_produto_id;
    RAISE NOTICE 'OK: produto % inserido com FKs validas', v_produto_id;

    -- CHECK (estoque >= 0)
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Estoque negativo', -1, 10.00, v_usuario_id, v_categoria_id);
        RAISE EXCEPTION 'FALHA: chk_produto_estoque nao bloqueou estoque negativo';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK: chk_produto_estoque bloqueou estoque negativo';
    END;

    -- CHECK (preco >= 0)
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Preco negativo', 1, -0.01, v_usuario_id, v_categoria_id);
        RAISE EXCEPTION 'FALHA: chk_produto_preco nao bloqueou preco negativo';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK: chk_produto_preco bloqueou preco negativo';
    END;

    -- UNIQUE (categoria.nome)
    BEGIN
        INSERT INTO categoria (nome) VALUES ('Eletronicos Teste #6');
        RAISE EXCEPTION 'FALHA: categoria.nome aceitou valor duplicado';
    EXCEPTION WHEN unique_violation THEN
        RAISE NOTICE 'OK: categoria.nome rejeitou valor duplicado';
    END;

    -- FK produto -> vendedor
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Vendedor inexistente', 1, 10.00, -1, v_categoria_id);
        RAISE EXCEPTION 'FALHA: fk_produto_vendedor nao bloqueou vendedor inexistente';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK: fk_produto_vendedor bloqueou vendedor inexistente';
    END;

    -- FK produto -> categoria
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Categoria inexistente', 1, 10.00, v_usuario_id, -1);
        RAISE EXCEPTION 'FALHA: fk_produto_categoria nao bloqueou categoria inexistente';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK: fk_produto_categoria bloqueou categoria inexistente';
    END;

    -- ON DELETE RESTRICT em produto -> categoria
    BEGIN
        DELETE FROM categoria WHERE id = v_categoria_id;
        RAISE EXCEPTION 'FALHA: categoria com produto vinculado foi removida';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'OK: ON DELETE RESTRICT protegeu categoria em uso';
    END;

    -- Limpeza (o DELETE do usuario remove vendedor e produto em cascata)
    DELETE FROM produto  WHERE id_produto = v_produto_id;
    DELETE FROM categoria WHERE id = v_categoria_id;
    DELETE FROM usuario   WHERE id = v_usuario_id;

    RAISE NOTICE 'Teste de validacao da issue #6 concluido com sucesso';
END $$;

RESET app.usuario_role;
