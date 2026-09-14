-- ============================================================
-- Issue #14: DDL + RLS de pedido e item_pedido

-- ------------------------------------------------------------
-- 1. Sequence
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pedido_id;

-- ------------------------------------------------------------
-- 2. Tabela pedido
-- ------------------------------------------------------------
-- status espelha o enum StatusPedido (CRIADO, CONFIRMADO, CANCELADO) do
-- domínio Java. valor_total é NUMERIC(10,2) para casar com o BigDecimal
-- da entidade. id_cupom é opcional (cupom é benefício, não obrigação).
CREATE TABLE pedido (
                        id_pedido    BIGINT        NOT NULL DEFAULT nextval('seq_pedido_id'),
                        data_pedido  TIMESTAMP     NOT NULL DEFAULT now(),
                        valor_total  NUMERIC(10,2) NOT NULL,
                        status       VARCHAR(20)   NOT NULL DEFAULT 'CRIADO',
                        id_cliente   BIGINT        NOT NULL,
                        id_cupom     BIGINT        NULL,
                        CONSTRAINT pk_pedido PRIMARY KEY (id_pedido),
                        CONSTRAINT chk_pedido_status CHECK (status IN ('CRIADO', 'CONFIRMADO', 'CANCELADO')),
                        CONSTRAINT chk_pedido_valor_total CHECK (valor_total >= 0),
                        CONSTRAINT fk_pedido_cliente FOREIGN KEY (id_cliente)
                            REFERENCES cliente (id) ON DELETE CASCADE,
    -- Cupom já foi usado no pedido: se o ADMIN apagar o cupom depois,
    -- o pedido não pode sumir junto — só perde a referência.
                        CONSTRAINT fk_pedido_cupom FOREIGN KEY (id_cupom)
                            REFERENCES cupom (id) ON DELETE SET NULL
);

-- ------------------------------------------------------------
-- 3. Tabela item_pedido (PK composta, sem coluna de dono)
-- ------------------------------------------------------------
CREATE TABLE item_pedido (
                             id_pedido      BIGINT        NOT NULL,
                             id_produto     BIGINT        NOT NULL,
                             quantidade     INTEGER       NOT NULL,
                             preco_unitario NUMERIC(10,2) NOT NULL,
                             CONSTRAINT pk_item_pedido PRIMARY KEY (id_pedido, id_produto),
                             CONSTRAINT fk_item_pedido_pedido FOREIGN KEY (id_pedido)
                                 REFERENCES pedido (id_pedido) ON DELETE CASCADE,
    -- RESTRICT (não CASCADE): produto vendido não pode ser apagado
    -- silenciosamente enquanto existir pedido histórico com ele.
                             CONSTRAINT fk_item_pedido_produto FOREIGN KEY (id_produto)
                                 REFERENCES produto (id_produto) ON DELETE RESTRICT,
                             CONSTRAINT chk_item_pedido_quantidade CHECK (quantidade > 0),
                             CONSTRAINT chk_item_pedido_preco CHECK (preco_unitario >= 0)
);

-- ------------------------------------------------------------
-- 4. Índices das FKs
-- ------------------------------------------------------------
CREATE INDEX idx_pedido_cliente      ON pedido (id_cliente);
CREATE INDEX idx_pedido_cupom        ON pedido (id_cupom);
CREATE INDEX idx_item_pedido_produto ON item_pedido (id_produto);

-- ------------------------------------------------------------
-- 5. Habilitar e forçar RLS
-- ------------------------------------------------------------
ALTER TABLE pedido      ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_pedido ENABLE ROW LEVEL SECURITY;

ALTER TABLE pedido      FORCE ROW LEVEL SECURITY;
ALTER TABLE item_pedido FORCE ROW LEVEL SECURITY;

-- ------------------------------------------------------------
-- 6. Policies de pedido
-- ------------------------------------------------------------
-- NULLIF(..., '') antes do cast: mesmo motivo das demais policies do
-- projeto (ver 04_fix_rls_identidade.sql) — evita 22P02 quando o GUC
-- chega como string vazia em vez de NULL.

-- SELECT: dono (cliente) OU vendedor com produto no pedido OU ADMIN.
-- O EXISTS navega item_pedido -> produto e casa id_vendedor com quem
-- está logado; um único item já basta para o pedido inteiro aparecer.
CREATE POLICY pedido_select ON pedido FOR SELECT
                                              USING (
                                              id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                              OR EXISTS (
                                              SELECT 1
                                              FROM item_pedido ip
                                              JOIN produto pr ON pr.id_produto = ip.id_produto
                                              WHERE ip.id_pedido = pedido.id_pedido
                                              AND pr.id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
                                              )
                                              OR current_setting('app.usuario_role', true) = 'ADMIN'
                                              );

-- INSERT: só dá para criar pedido em nome próprio (ou ADMIN, em nome de
-- alguém, ex.: suporte lançando pedido manual).
CREATE POLICY pedido_insert ON pedido FOR INSERT
    WITH CHECK (
        id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN'
    );

-- UPDATE: dono ou ADMIN. USING escolhe as linhas alcançáveis;
-- WITH CHECK impede o cliente de "transferir" o pedido para outro id.
-- Importante: aqui é só o dono (cliente), não o vendedor — vendedor
-- enxerga (SELECT) para conseguir despachar, mas não edita o pedido.
CREATE POLICY pedido_update ON pedido FOR UPDATE
                                                     USING (
                                                     id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                     OR current_setting('app.usuario_role', true) = 'ADMIN'
                                                     )
                                          WITH CHECK (
                                                     id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                     OR current_setting('app.usuario_role', true) = 'ADMIN'
                                                     );

-- DELETE: apenas ADMIN (nem o cliente apaga o próprio pedido — cancela
-- via UPDATE de status; histórico de venda não pode ser removido pelo
-- comprador nem pelo vendedor).
CREATE POLICY pedido_delete ON pedido FOR DELETE
USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- ------------------------------------------------------------
-- 7. Policies de item_pedido (mesma lógica, navegando por EXISTS)
-- ------------------------------------------------------------

-- SELECT: cliente dono do pedido OU vendedor dono do produto daquele
-- item OU ADMIN.
CREATE POLICY item_pedido_select ON item_pedido FOR SELECT
                                                        USING (
                                                        EXISTS (
                                                        SELECT 1
                                                        FROM pedido p
                                                        WHERE p.id_pedido = item_pedido.id_pedido
                                                        AND p.id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                        )
                                                        OR EXISTS (
                                                        SELECT 1
                                                        FROM produto pr
                                                        WHERE pr.id_produto = item_pedido.id_produto
                                                        AND pr.id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                        )
                                                        OR current_setting('app.usuario_role', true) = 'ADMIN'
                                                        );

-- INSERT/UPDATE/DELETE: só quem monta o pedido (o cliente dono) ou
-- ADMIN mexe no conteúdo. Vendedor só lê (para despachar); ele não
-- altera item de pedido de terceiros.
CREATE POLICY item_pedido_insert ON item_pedido FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1
              FROM pedido p
             WHERE p.id_pedido = item_pedido.id_pedido
               AND p.id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
        )
        OR current_setting('app.usuario_role', true) = 'ADMIN'
    );

CREATE POLICY item_pedido_update ON item_pedido FOR UPDATE
                                                               USING (
                                                               EXISTS (
                                                               SELECT 1
                                                               FROM pedido p
                                                               WHERE p.id_pedido = item_pedido.id_pedido
                                                               AND p.id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                               )
                                                               OR current_setting('app.usuario_role', true) = 'ADMIN'
                                                               )
                                                    WITH CHECK (
                                                               EXISTS (
                                                               SELECT 1
                                                               FROM pedido p
                                                               WHERE p.id_pedido = item_pedido.id_pedido
                                                               AND p.id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
                                                               )
                                                               OR current_setting('app.usuario_role', true) = 'ADMIN'
                                                               );

CREATE POLICY item_pedido_delete ON item_pedido FOR DELETE
USING (
        EXISTS (
            SELECT 1
              FROM pedido p
             WHERE p.id_pedido = item_pedido.id_pedido
               AND p.id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
        )
        OR current_setting('app.usuario_role', true) = 'ADMIN'
    );

-- ------------------------------------------------------------
-- 8. Grants
-- ------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE
      ON pedido, item_pedido
          TO app_ecommerce;

GRANT USAGE, SELECT
    ON SEQUENCE seq_pedido_id
    TO app_ecommerce;

-- ============================================================
-- 9. Teste de validação (critério de aceite da issue #14)
--
--    "Vendedor X vê o pedido que contém produto dele, mas SELECT
--     como vendedor Y (sem produto no pedido) não retorna a linha."
--
--    ATENÇÃO: este bloco só tem valor se executado por uma role SEM
--    BYPASSRLS. As policies não se aplicam a quem tem esse atributo, e
--    no Supabase tanto `postgres` quanto `service_role` têm. Rodando o
--    arquivo pelo SQL Editor (que conecta como postgres) o teste é
--    PULADO de propósito — valide conectando como app_ecommerce.
-- ============================================================
DO $$
DECLARE
v_cliente   BIGINT;
    v_vend_x    BIGINT;
    v_vend_y    BIGINT;
    v_cat       BIGINT;
    v_prod_x    BIGINT;
    v_prod_y    BIGINT;
    v_pedido    BIGINT;
    n INT;
BEGIN
    IF (SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user) THEN
        RAISE NOTICE '================================================================';
RAISE NOTICE 'TESTE PULADO: a role % possui BYPASSRLS.', current_user;
        RAISE NOTICE 'As policies nao se aplicam a ela, entao o teste nao provaria nada.';
        RAISE NOTICE 'Para validar a issue #14, execute esta secao como app_ecommerce.';
        RAISE NOTICE '================================================================';
        RETURN;
END IF;

    -- ---------- cenario, montado como ADMIN ----------
    -- app.usuario_id precisa de um valor VALIDO desde o inicio (mesmo
    -- motivo das demais issues: sob o pooler o GUC e herdado como
    -- string vazia entre transacoes).
    PERFORM set_config('app.usuario_id', '0', true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);

INSERT INTO usuario (nome, email, senha_hash, role, dtype)
VALUES ('Cliente #14', 'cliente.issue14@exemplo.local', 'hash', 'CLIENTE', 'Cliente')
    RETURNING id INTO v_cliente;
INSERT INTO cliente (id, telefone) VALUES (v_cliente, '(83) 90000-0014');

INSERT INTO usuario (nome, email, senha_hash, role, dtype)
VALUES ('Vendedor X #14', 'vendedor.x.issue14@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
    RETURNING id INTO v_vend_x;
INSERT INTO vendedor (id, razao_social, cnpj_cpf)
VALUES (v_vend_x, 'Loja X LTDA', '14140000000191');

INSERT INTO usuario (nome, email, senha_hash, role, dtype)
VALUES ('Vendedor Y #14', 'vendedor.y.issue14@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
    RETURNING id INTO v_vend_y;
INSERT INTO vendedor (id, razao_social, cnpj_cpf)
VALUES (v_vend_y, 'Loja Y LTDA', '14140000000272');

INSERT INTO categoria (nome, descricao)
VALUES ('Categoria Teste #14', 'Removida ao final do teste')
    RETURNING id INTO v_cat;

-- Só o produto de X entra no pedido; Y não tem produto nenhum nele.
INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
VALUES ('Produto do Vendedor X', 10, 100.00, v_vend_x, v_cat)
    RETURNING id_produto INTO v_prod_x;
INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
VALUES ('Produto do Vendedor Y', 10, 50.00, v_vend_y, v_cat)
    RETURNING id_produto INTO v_prod_y;

-- ---------- a partir daqui, sessao do CLIENTE ----------
PERFORM set_config('app.usuario_role', 'CLIENTE', true);
    PERFORM set_config('app.usuario_id', v_cliente::text, true);

INSERT INTO pedido (valor_total, status, id_cliente)
VALUES (100.00, 'CRIADO', v_cliente)
    RETURNING id_pedido INTO v_pedido;
RAISE NOTICE 'OK: cliente criou o proprio pedido (id=%)', v_pedido;

INSERT INTO item_pedido (id_pedido, id_produto, quantidade, preco_unitario)
VALUES (v_pedido, v_prod_x, 1, 100.00);
RAISE NOTICE 'OK: cliente adicionou item do produto de X ao proprio pedido';

    -- cliente não consegue montar pedido em nome de outro
BEGIN
INSERT INTO pedido (valor_total, status, id_cliente)
VALUES (10.00, 'CRIADO', v_cliente + 999999);
RAISE EXCEPTION 'FALHA: cliente criou pedido em nome de outro id';
EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: pedido_insert bloqueou pedido em nome de outro cliente';
END;

    -- ---------- CRITERIO DE ACEITE: sessao do VENDEDOR X ----------
    PERFORM set_config('app.usuario_role', 'VENDEDOR', true);
    PERFORM set_config('app.usuario_id', v_vend_x::text, true);

SELECT count(*) INTO n FROM pedido WHERE id_pedido = v_pedido;
IF n <> 1 THEN RAISE EXCEPTION 'FALHA: vendedor X (com produto no pedido) nao enxergou o pedido'; END IF;
    RAISE NOTICE 'OK: vendedor X ve o pedido que contem produto dele';

SELECT count(*) INTO n FROM item_pedido WHERE id_pedido = v_pedido;
IF n <> 1 THEN RAISE EXCEPTION 'FALHA: vendedor X nao enxergou o item_pedido do proprio produto'; END IF;
    RAISE NOTICE 'OK: vendedor X ve o item_pedido do proprio produto';

    -- vendedor não edita conteúdo do pedido, só enxerga
BEGIN
UPDATE item_pedido SET quantidade = 99
WHERE id_pedido = v_pedido AND id_produto = v_prod_x;
RAISE EXCEPTION 'FALHA: vendedor X conseguiu alterar item_pedido';
EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: item_pedido_update bloqueou vendedor (nao-dono do pedido)';
END;

    -- ---------- CRITERIO DE ACEITE: sessao do VENDEDOR Y ----------
    PERFORM set_config('app.usuario_role', 'VENDEDOR', true);
    PERFORM set_config('app.usuario_id', v_vend_y::text, true);

SELECT count(*) INTO n FROM pedido WHERE id_pedido = v_pedido;
IF n <> 0 THEN RAISE EXCEPTION 'FALHA: vendedor Y (sem produto no pedido) enxergou o pedido'; END IF;
    RAISE NOTICE 'OK: vendedor Y (sem produto no pedido) NAO enxerga o pedido';

SELECT count(*) INTO n FROM item_pedido WHERE id_pedido = v_pedido;
IF n <> 0 THEN RAISE EXCEPTION 'FALHA: vendedor Y enxergou item_pedido de pedido alheio'; END IF;
    RAISE NOTICE 'OK: vendedor Y NAO enxerga o item_pedido do pedido alheio';

    -- vendedor Y nem apaga pedido alheio (DELETE é só ADMIN)
DELETE FROM pedido WHERE id_pedido = v_pedido;
GET DIAGNOSTICS n = ROW_COUNT;
IF n <> 0 THEN RAISE EXCEPTION 'FALHA: vendedor Y conseguiu apagar pedido alheio'; END IF;
    RAISE NOTICE 'OK: DELETE de vendedor Y em pedido alheio afetou 0 linhas';

    -- ---------- cliente tambem nao apaga o proprio pedido ----------
    PERFORM set_config('app.usuario_role', 'CLIENTE', true);
    PERFORM set_config('app.usuario_id', v_cliente::text, true);

DELETE FROM pedido WHERE id_pedido = v_pedido;
GET DIAGNOSTICS n = ROW_COUNT;
IF n <> 0 THEN RAISE EXCEPTION 'FALHA: cliente apagou o proprio pedido (deveria ser so ADMIN)'; END IF;
    RAISE NOTICE 'OK: pedido_delete exigiu ADMIN (cliente nao apaga o proprio pedido)';

    -- cliente altera o proprio pedido (ex.: cancelar)
UPDATE pedido SET status = 'CANCELADO' WHERE id_pedido = v_pedido;
GET DIAGNOSTICS n = ROW_COUNT;
IF n <> 1 THEN RAISE EXCEPTION 'FALHA: cliente nao conseguiu atualizar o proprio pedido (% linhas)', n; END IF;
    RAISE NOTICE 'OK: cliente atualizou o proprio pedido (ex.: cancelar)';

    -- ---------- limpeza, de volta como ADMIN ----------
    PERFORM set_config('app.usuario_id', v_cliente::text, true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);
DELETE FROM item_pedido WHERE id_pedido = v_pedido;
DELETE FROM pedido      WHERE id_pedido = v_pedido;
DELETE FROM produto     WHERE id_produto IN (v_prod_x, v_prod_y);
DELETE FROM categoria   WHERE id = v_cat;
DELETE FROM usuario     WHERE id IN (v_cliente, v_vend_x, v_vend_y);

RAISE NOTICE 'Teste de validacao da issue #14 concluido com sucesso';
END $$;