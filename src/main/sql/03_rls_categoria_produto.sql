-- ============================================================
-- Issue #7: RLS de categoria e produto
-- Pré-requisito: 02_ddl_categoria_produto.sql
--
-- Modelo: leitura é vitrine (pública), escrita é do dono.
--   categoria -> SELECT para todos; escrita só ADMIN
--   produto   -> SELECT para todos; escrita só do vendedor dono, ou ADMIN
-- ============================================================

-- 1. Habilitar e forçar RLS
--    ENABLE é idempotente: o event trigger ensure_rls do Supabase já liga
--    RLS em toda tabela nova, mas sem FORCE e sem policy nenhuma.
ALTER TABLE categoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE produto   ENABLE ROW LEVEL SECURITY;

--    FORCE submete também o dono da tabela às policies, igual à issue #4.
ALTER TABLE categoria FORCE ROW LEVEL SECURITY;
ALTER TABLE produto   FORCE ROW LEVEL SECURITY;

-- ------------------------------------------------------------
-- 2. Policies de categoria
-- ------------------------------------------------------------

-- Vitrine: qualquer sessão lê o catálogo, inclusive a anônima.
CREATE POLICY categoria_select ON categoria FOR SELECT
    USING (true);

-- Categoria é dado de referência compartilhado: só ADMIN mexe.
CREATE POLICY categoria_insert ON categoria FOR INSERT
    WITH CHECK (current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY categoria_update ON categoria FOR UPDATE
    USING      (current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY categoria_delete ON categoria FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- ------------------------------------------------------------
-- 3. Policies de produto
-- ------------------------------------------------------------
-- NULLIF(..., '') antes do cast: current_setting devolve string vazia
-- (não NULL) quando o setting foi zerado na sessão, e ''::bigint levanta
-- 22P02. Sem o NULLIF, uma requisição anônima receberia erro de sintaxe
-- em vez de simplesmente ter o acesso negado.

-- Vitrine: o catálogo é público, inclusive para sessão anônima.
CREATE POLICY produto_select ON produto FOR SELECT
    USING (true);

-- Só dá para cadastrar produto em nome de si mesmo.
CREATE POLICY produto_insert ON produto FOR INSERT
    WITH CHECK (id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- USING escolhe quais linhas o vendedor enxerga para alterar;
-- WITH CHECK impede que ele transfira o próprio produto para outro vendedor.
CREATE POLICY produto_update ON produto FOR UPDATE
    USING      (id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY produto_delete ON produto FOR DELETE
    USING (id_vendedor = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

-- ============================================================
-- 4. Teste de validação (critério de aceite da issue #7)
--
--    Cria dois vendedores (A e B), assume a identidade de A e prova que
--    ele só alcança os próprios produtos. Remove tudo que criou ao final.
--
--    ATENÇÃO: este bloco só tem valor se executado por uma role SEM
--    BYPASSRLS. As policies não se aplicam a quem tem esse atributo, e no
--    Supabase tanto `postgres` quanto `service_role` têm. Rodando o arquivo
--    pelo SQL Editor (que conecta como postgres) o teste é PULADO de
--    propósito — valide conectando como app_ecommerce.
-- ============================================================
DO $$
DECLARE
    v_vend_a BIGINT; v_vend_b BIGINT; v_cat BIGINT;
    v_prod_a BIGINT; v_prod_b BIGINT; v_prod_novo BIGINT;
    n INT;
BEGIN
    IF (SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user) THEN
        RAISE NOTICE '================================================================';
        RAISE NOTICE 'TESTE PULADO: a role % possui BYPASSRLS.', current_user;
        RAISE NOTICE 'As policies nao se aplicam a ela, entao o teste nao provaria nada.';
        RAISE NOTICE 'Para validar a issue #7, execute esta secao como app_ecommerce.';
        RAISE NOTICE '================================================================';
        RETURN;
    END IF;

    -- ---------- cenario, montado como ADMIN ----------
    -- app.usuario_id precisa de um valor VALIDO desde o inicio. Sob o pooler
    -- do Supabase o GUC e herdado como string vazia entre transacoes, e as
    -- policies da issue #4 castam sem NULLIF -> 22P02. O valor 0 nao
    -- corresponde a usuario nenhum; quem libera aqui e o role ADMIN.
    PERFORM set_config('app.usuario_id', '0', true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);

    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Vendedor A #7', 'vendedor.a.issue7@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
      RETURNING id INTO v_vend_a;
    INSERT INTO vendedor (id, razao_social, cnpj_cpf)
         VALUES (v_vend_a, 'Loja A LTDA', '11111111000191');

    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Vendedor B #7', 'vendedor.b.issue7@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
      RETURNING id INTO v_vend_b;
    INSERT INTO vendedor (id, razao_social, cnpj_cpf)
         VALUES (v_vend_b, 'Loja B LTDA', '22222222000191');

    INSERT INTO categoria (nome, descricao)
         VALUES ('Categoria Teste #7', 'Removida ao final do teste')
      RETURNING id INTO v_cat;

    INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
         VALUES ('Produto do A', 10, 100.00, v_vend_a, v_cat) RETURNING id_produto INTO v_prod_a;
    INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
         VALUES ('Produto do B', 10, 100.00, v_vend_b, v_cat) RETURNING id_produto INTO v_prod_b;
    RAISE NOTICE 'cenario montado: vendedor A=%, vendedor B=%', v_vend_a, v_vend_b;

    -- ---------- a partir daqui, sessao do VENDEDOR A ----------
    PERFORM set_config('app.usuario_role', 'VENDEDOR', true);
    PERFORM set_config('app.usuario_id', v_vend_a::text, true);

    -- 1) vitrine: A enxerga o produto de B (leitura e publica)
    SELECT count(*) INTO n FROM produto WHERE id_produto IN (v_prod_a, v_prod_b);
    IF n <> 2 THEN RAISE EXCEPTION 'FALHA: SELECT publico nao devolveu os 2 produtos (veio %)', n; END IF;
    RAISE NOTICE 'OK: SELECT publico - A enxerga os produtos de B';

    -- 2) A cadastra em nome proprio
    INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
         VALUES ('Produto novo do A', 5, 50.00, v_vend_a, v_cat) RETURNING id_produto INTO v_prod_novo;
    RAISE NOTICE 'OK: A cadastrou produto em nome proprio (id=%)', v_prod_novo;

    -- 3) A tenta cadastrar EM NOME DE B  -> tarefa explicita da issue
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Produto pirata', 1, 10.00, v_vend_b, v_cat);
        RAISE EXCEPTION 'FALHA: A conseguiu cadastrar produto em nome de B';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: produto_insert bloqueou cadastro em nome de outro vendedor';
    END;

    -- 4) A altera o proprio produto -> 1 linha
    UPDATE produto SET estoque = 99 WHERE id_produto = v_prod_a;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 1 THEN RAISE EXCEPTION 'FALHA: A nao conseguiu alterar o proprio produto (% linhas)', n; END IF;
    RAISE NOTICE 'OK: A alterou o proprio produto (1 linha)';

    -- 5) A tenta alterar o produto de B -> 0 linhas  (CRITERIO DE ACEITE)
    UPDATE produto SET estoque = 99 WHERE id_produto = v_prod_b;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: UPDATE em produto de outro vendedor afetou % linhas', n; END IF;
    RAISE NOTICE 'OK: UPDATE em produto de B afetou 0 linhas';

    -- 6) A tenta apagar o produto de B -> 0 linhas
    DELETE FROM produto WHERE id_produto = v_prod_b;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: DELETE em produto de outro vendedor afetou % linhas', n; END IF;
    RAISE NOTICE 'OK: DELETE em produto de B afetou 0 linhas';

    -- 7) A tenta TRANSFERIR o proprio produto para B (WITH CHECK do UPDATE)
    BEGIN
        UPDATE produto SET id_vendedor = v_vend_b WHERE id_produto = v_prod_a;
        RAISE EXCEPTION 'FALHA: A transferiu o proprio produto para B';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: WITH CHECK impediu transferencia de produto para outro vendedor';
    END;

    -- 8) VENDEDOR nao mexe em categoria
    BEGIN
        INSERT INTO categoria (nome) VALUES ('Categoria Pirata #7');
        RAISE EXCEPTION 'FALHA: VENDEDOR conseguiu criar categoria';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: categoria_insert exigiu ADMIN';
    END;

    -- 9) mas VENDEDOR le categoria normalmente
    SELECT count(*) INTO n FROM categoria WHERE id = v_cat;
    IF n <> 1 THEN RAISE EXCEPTION 'FALHA: VENDEDOR nao conseguiu ler categoria'; END IF;
    RAISE NOTICE 'OK: categoria_select liberou leitura para VENDEDOR';

    -- ---------- sessao ANONIMA (sem contexto, como o filtro JWT deixa) ----------
    PERFORM set_config('app.usuario_id', '', true);
    PERFORM set_config('app.usuario_role', '', true);

    -- 10) vitrine anonima le
    SELECT count(*) INTO n FROM produto WHERE id_produto IN (v_prod_a, v_prod_b);
    IF n <> 2 THEN RAISE EXCEPTION 'FALHA: sessao anonima nao leu a vitrine (veio %)', n; END IF;
    RAISE NOTICE 'OK: sessao anonima le a vitrine';

    -- 11) vitrine anonima nao escreve (e o NULLIF evita 22P02 aqui)
    BEGIN
        INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
             VALUES ('Produto anonimo', 1, 10.00, v_vend_a, v_cat);
        RAISE EXCEPTION 'FALHA: sessao anonima cadastrou produto';
    EXCEPTION
        WHEN insufficient_privilege THEN
            RAISE NOTICE 'OK: sessao anonima bloqueada no INSERT';
        WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'FALHA: cast de string vazia estourou 22P02 - falta NULLIF na policy';
    END;

    -- ---------- limpeza, de volta como ADMIN ----------
    -- Restaura um app.usuario_id valido ANTES de tocar em usuario. As policies
    -- da issue #4 fazem current_setting(...)::bigint sem NULLIF, entao a string
    -- vazia deixada pelo teste 11 levanta 22P02 dentro de usuario_select.
    PERFORM set_config('app.usuario_id', v_vend_a::text, true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);
    DELETE FROM produto   WHERE id_produto IN (v_prod_a, v_prod_b, v_prod_novo);
    DELETE FROM categoria WHERE id = v_cat;
    DELETE FROM usuario   WHERE id IN (v_vend_a, v_vend_b);

    RAISE NOTICE 'Teste de validacao da issue #7 concluido com sucesso';
END $$;
