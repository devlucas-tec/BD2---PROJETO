-- ============================================================
-- Correção das policies de identidade da issue #4
-- Pré-requisito: 01_grants_app_ecommerce.sql
--
-- PROBLEMA
-- As 8 policies de SELECT e UPDATE de usuario, admin, cliente e vendedor
-- fazem current_setting('app.usuario_id', true)::bigint sem proteção,
-- apoiadas na premissa de que uma sessão anônima produz NULL.
--
-- A premissa não vale sob o pooler. Medido neste banco:
--
--   GUC nunca tocado naquele backend  -> NULL   -> cast ok
--   GUC ja usado naquele backend      -> ''     -> 22P02
--
-- set_config cria um placeholder cujo valor de reset é string vazia, e o
-- Supavisor (porta 6543) reusa backends entre transações. Uma requisição
-- anônima herda o '' de uma requisição autenticada anterior e recebe
-- "invalid input syntax for type bigint" em vez de ter o acesso negado.
--
-- CORREÇÃO
-- Envolver o current_setting em NULLIF(..., '') antes do cast, mesmo
-- padrão já usado nas policies de produto em 03_rls_categoria_produto.sql.
-- A semântica é idêntica para todo valor valido; muda so o caso da string
-- vazia, que passa a virar NULL e negar o acesso em vez de levantar erro.
--
-- Feito com ALTER POLICY de propósito: o 01 já foi aplicado, e reescrever
-- migration aplicada não corrigiria bancos existentes.
-- ============================================================

-- 1. usuario
ALTER POLICY usuario_select ON usuario
    USING (id = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

ALTER POLICY usuario_update ON usuario
    USING      (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- 2. admin
ALTER POLICY admin_select ON admin
    USING (id = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

ALTER POLICY admin_update ON admin
    USING      (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- 3. cliente
ALTER POLICY cliente_select ON cliente
    USING (id = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

ALTER POLICY cliente_update ON cliente
    USING      (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- 4. vendedor
ALTER POLICY vendedor_select ON vendedor
    USING (id = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

ALTER POLICY vendedor_update ON vendedor
    USING      (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- ============================================================
-- 5. Teste de validação
--
--    Simula o estado herdado do pooler (app.usuario_id = '') e verifica
--    que as 4 tabelas negam o acesso em silêncio, em vez de levantar 22P02.
--
--    ATENÇÃO: só vale executado por role SEM BYPASSRLS. No Supabase,
--    postgres e service_role têm, e para elas as policies nem são
--    avaliadas. Rodando pelo SQL Editor o teste é PULADO de propósito.
-- ============================================================
DO $$
DECLARE n INT;
BEGIN
    IF (SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user) THEN
        RAISE NOTICE '================================================================';
        RAISE NOTICE 'TESTE PULADO: a role % possui BYPASSRLS.', current_user;
        RAISE NOTICE 'As policies nao sao avaliadas para ela, o teste nao provaria nada.';
        RAISE NOTICE 'Valide conectando como app_ecommerce.';
        RAISE NOTICE '================================================================';
        RETURN;
    END IF;

    -- Estado exato que o pooler deixa numa transacao seguinte.
    PERFORM set_config('app.usuario_id', '', true);
    PERFORM set_config('app.usuario_role', '', true);

    -- SELECT: as 4 policies _select
    BEGIN
        SELECT count(*) INTO n FROM usuario;
        RAISE NOTICE 'OK: usuario_select negou em silencio (% linhas)', n;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: usuario_select ainda levanta 22P02';
    END;

    BEGIN
        SELECT count(*) INTO n FROM admin;
        RAISE NOTICE 'OK: admin_select negou em silencio (% linhas)', n;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: admin_select ainda levanta 22P02';
    END;

    BEGIN
        SELECT count(*) INTO n FROM cliente;
        RAISE NOTICE 'OK: cliente_select negou em silencio (% linhas)', n;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: cliente_select ainda levanta 22P02';
    END;

    BEGIN
        SELECT count(*) INTO n FROM vendedor;
        RAISE NOTICE 'OK: vendedor_select negou em silencio (% linhas)', n;
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: vendedor_select ainda levanta 22P02';
    END;

    -- UPDATE: as 4 policies _update. "SET nome = nome" e no-op mesmo se passasse.
    BEGIN
        UPDATE usuario SET nome = nome WHERE id IS NOT NULL;
        GET DIAGNOSTICS n = ROW_COUNT;
        IF n <> 0 THEN RAISE EXCEPTION 'FALHA: usuario_update deixou passar % linha(s)', n; END IF;
        RAISE NOTICE 'OK: usuario_update negou em silencio (0 linhas)';
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: usuario_update ainda levanta 22P02';
    END;

    BEGIN
        UPDATE admin SET id = id WHERE id IS NOT NULL;
        GET DIAGNOSTICS n = ROW_COUNT;
        IF n <> 0 THEN RAISE EXCEPTION 'FALHA: admin_update deixou passar % linha(s)', n; END IF;
        RAISE NOTICE 'OK: admin_update negou em silencio (0 linhas)';
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: admin_update ainda levanta 22P02';
    END;

    BEGIN
        UPDATE cliente SET telefone = telefone WHERE id IS NOT NULL;
        GET DIAGNOSTICS n = ROW_COUNT;
        IF n <> 0 THEN RAISE EXCEPTION 'FALHA: cliente_update deixou passar % linha(s)', n; END IF;
        RAISE NOTICE 'OK: cliente_update negou em silencio (0 linhas)';
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: cliente_update ainda levanta 22P02';
    END;

    BEGIN
        UPDATE vendedor SET razao_social = razao_social WHERE id IS NOT NULL;
        GET DIAGNOSTICS n = ROW_COUNT;
        IF n <> 0 THEN RAISE EXCEPTION 'FALHA: vendedor_update deixou passar % linha(s)', n; END IF;
        RAISE NOTICE 'OK: vendedor_update negou em silencio (0 linhas)';
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: vendedor_update ainda levanta 22P02';
    END;

    RAISE NOTICE 'Correcao das policies da issue #4 validada: 8 de 8';
END $$;
