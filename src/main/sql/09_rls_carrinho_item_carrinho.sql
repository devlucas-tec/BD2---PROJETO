-- ============================================================
-- 09_rls_carrinho_item_carrinho.sql
-- Issue #12: RLS e políticas de carrinho e item_carrinho
-- ============================================================

-- ------------------------------------------------------------
-- 1) ENABLE + FORCE ROW LEVEL SECURITY em ambas as tabelas
-- ------------------------------------------------------------
ALTER TABLE carrinho ENABLE ROW LEVEL SECURITY;
ALTER TABLE carrinho FORCE ROW LEVEL SECURITY;

ALTER TABLE item_carrinho ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_carrinho FORCE ROW LEVEL SECURITY;

-- Observação: FORCE RLS não afeta o DONO (owner) da tabela nem
-- roles com BYPASSRLS. No Supabase, garanta que a aplicação
-- conecta com um role comum (ex: authenticated/app_user), não
-- com o role postgres/owner das tabelas.

-- ------------------------------------------------------------
-- 2) Políticas: carrinho
--    Todas as operações restritas a id_cliente = usuário logado
--    (ou ADMIN, no caso do SELECT)
-- ------------------------------------------------------------
DROP POLICY IF EXISTS carrinho_select ON carrinho;
DROP POLICY IF EXISTS carrinho_insert ON carrinho;
DROP POLICY IF EXISTS carrinho_update ON carrinho;
DROP POLICY IF EXISTS carrinho_delete ON carrinho;

-- SELECT: dono do carrinho, ou ADMIN vê tudo
CREATE POLICY carrinho_select ON carrinho
    FOR SELECT
                                       USING (
                                       id_cliente = current_setting('app.usuario_id', true)::bigint
                                       OR current_setting('app.usuario_role', true) = 'ADMIN'
                                       );

-- INSERT: só pode criar carrinho vinculado a si mesmo
CREATE POLICY carrinho_insert ON carrinho
    FOR INSERT
    WITH CHECK (
        id_cliente = current_setting('app.usuario_id', true)::bigint
    );

-- UPDATE: só edita o próprio carrinho, e não pode "trocar de dono"
CREATE POLICY carrinho_update ON carrinho
    FOR UPDATE
                          USING (id_cliente = current_setting('app.usuario_id', true)::bigint)
        WITH CHECK (id_cliente = current_setting('app.usuario_id', true)::bigint);

-- DELETE: só apaga o próprio carrinho
CREATE POLICY carrinho_delete ON carrinho
    FOR DELETE
USING (id_cliente = current_setting('app.usuario_id', true)::bigint);


-- ------------------------------------------------------------
-- 3) Políticas: item_carrinho
--    Não tem coluna de dono -> navega até carrinho via EXISTS
-- ------------------------------------------------------------
DROP POLICY IF EXISTS item_carrinho_select ON item_carrinho;
DROP POLICY IF EXISTS item_carrinho_insert ON item_carrinho;
DROP POLICY IF EXISTS item_carrinho_update ON item_carrinho;
DROP POLICY IF EXISTS item_carrinho_delete ON item_carrinho;

-- SELECT
CREATE POLICY item_carrinho_select ON item_carrinho
    FOR SELECT
                                       USING (
                                       EXISTS (
                                       SELECT 1 FROM carrinho c
                                       WHERE c.id = item_carrinho.carrinho_id
                                       AND c.id_cliente = current_setting('app.usuario_id', true)::bigint
                                       )
                                       OR current_setting('app.usuario_role', true) = 'ADMIN'
                                       );

-- INSERT: só insere item em carrinho que é seu
CREATE POLICY item_carrinho_insert ON item_carrinho
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM carrinho c
            WHERE c.id = item_carrinho.carrinho_id
              AND c.id_cliente = current_setting('app.usuario_id', true)::bigint
        )
    );

-- UPDATE: USING garante que só mexe em item de carrinho próprio;
-- WITH CHECK impede "mover" o item para o carrinho de outro cliente
-- (o carrinho_id novo, pós-update, também precisa ser seu)
CREATE POLICY item_carrinho_update ON item_carrinho
    FOR UPDATE
                          USING (
                          EXISTS (
                          SELECT 1 FROM carrinho c
                          WHERE c.id = item_carrinho.carrinho_id
                          AND c.id_cliente = current_setting('app.usuario_id', true)::bigint
                          )
                          )
        WITH CHECK (
                          EXISTS (
                          SELECT 1 FROM carrinho c
                          WHERE c.id = item_carrinho.carrinho_id
                          AND c.id_cliente = current_setting('app.usuario_id', true)::bigint
                          )
                          );

-- DELETE: só remove item do próprio carrinho
CREATE POLICY item_carrinho_delete ON item_carrinho
    FOR DELETE
USING (
        EXISTS (
            SELECT 1 FROM carrinho c
            WHERE c.id = item_carrinho.carrinho_id
              AND c.id_cliente = current_setting('app.usuario_id', true)::bigint
        )
    );


-- ------------------------------------------------------------
-- 4) Grants
-- ------------------------------------------------------------
-- carrinho já recebeu GRANT em 06_ddl_carrinho.sql
-- item_carrinho já recebeu GRANT em 07_ddl_item_carrinho.sql
-- Nada a fazer aqui — RLS atua em cima dos grants já concedidos.