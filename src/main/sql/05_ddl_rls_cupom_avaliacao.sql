-- ============================================================
-- Issue #9: DDL + RLS das tabelas cupom e avaliacao
-- Pré-requisitos:
--   01_grants_app_ecommerce.sql (usuario/cliente + role app_ecommerce)
--   02_ddl_categoria_produto.sql (produto)
--   04_fix_rls_identidade.sql (NULLIF nas policies de identidade)
--
-- Modelo de acesso:
--   cupom     -> catálogo restrito: ADMIN vê tudo; os demais só veem cupom
--                utilizável (ATIVO e não expirado). Escrita só ADMIN.
--   avaliacao -> leitura pública (faz parte da vitrine); cada cliente
--                assina a própria avaliação e só mexe nela.
-- ============================================================

-- ------------------------------------------------------------
-- 1. Sequences
-- ------------------------------------------------------------
CREATE SEQUENCE seq_cupom_id;
CREATE SEQUENCE seq_avaliacao_id;

-- ------------------------------------------------------------
-- 2. Tabela cupom
-- ------------------------------------------------------------
-- status espelha o enum StatusCupom (ATIVO, INATIVO) do domínio Java.
-- valor_desconto é NUMERIC(10,2) para casar com o BigDecimal da entidade.
-- data_expiracao é DATE porque Cupom.dataExpiracao é LocalDate.
CREATE TABLE cupom (
    id             BIGINT        NOT NULL DEFAULT nextval('seq_cupom_id'),
    codigo         VARCHAR(50)   NOT NULL UNIQUE,
    valor_desconto NUMERIC(10,2) NOT NULL,
    data_expiracao DATE          NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT pk_cupom PRIMARY KEY (id),
    CONSTRAINT chk_cupom_status CHECK (status IN ('ATIVO', 'INATIVO')),
    -- Mesmo espírito de chk_produto_preco: desconto negativo ou zero não é cupom.
    CONSTRAINT chk_cupom_valor CHECK (valor_desconto > 0)
);

-- ------------------------------------------------------------
-- 3. Tabela avaliacao
-- ------------------------------------------------------------
-- data_avaliacao é TIMESTAMP porque Avaliacao.dataAvaliacao é LocalDateTime.
-- Os dois ON DELETE CASCADE são deliberados: avaliação não sobrevive nem ao
-- cliente que a escreveu nem ao produto avaliado.
CREATE TABLE avaliacao (
    id             BIGINT    NOT NULL DEFAULT nextval('seq_avaliacao_id'),
    nota           INTEGER   NOT NULL,
    comentario     TEXT,
    data_avaliacao TIMESTAMP NOT NULL DEFAULT now(),
    id_cliente     BIGINT    NOT NULL,
    id_produto     BIGINT    NOT NULL,
    CONSTRAINT pk_avaliacao PRIMARY KEY (id),
    CONSTRAINT chk_avaliacao_nota CHECK (nota BETWEEN 1 AND 5),
    CONSTRAINT fk_avaliacao_cliente FOREIGN KEY (id_cliente)
        REFERENCES cliente (id) ON DELETE CASCADE,
    CONSTRAINT fk_avaliacao_produto FOREIGN KEY (id_produto)
        REFERENCES produto (id_produto) ON DELETE CASCADE
);

-- ------------------------------------------------------------
-- 4. Índices das FKs
-- ------------------------------------------------------------
-- (cupom.codigo já tem índice implícito criado pela constraint UNIQUE)
CREATE INDEX idx_avaliacao_cliente ON avaliacao (id_cliente);
CREATE INDEX idx_avaliacao_produto ON avaliacao (id_produto);

-- ------------------------------------------------------------
-- 5. Habilitar e forçar RLS
-- ------------------------------------------------------------
-- ENABLE é idempotente: o event trigger ensure_rls do Supabase já liga RLS
-- em toda tabela nova, mas sem FORCE e sem policy nenhuma.
ALTER TABLE cupom     ENABLE ROW LEVEL SECURITY;
ALTER TABLE avaliacao ENABLE ROW LEVEL SECURITY;

-- FORCE submete também o dono da tabela às policies, igual às issues #4 e #7.
ALTER TABLE cupom     FORCE ROW LEVEL SECURITY;
ALTER TABLE avaliacao FORCE ROW LEVEL SECURITY;

-- ------------------------------------------------------------
-- 6. Policies de cupom
-- ------------------------------------------------------------
-- NOTA SOBRE O ESCOPO DA LEITURA
-- A tarefa da issue diz "demais papéis só veem status = 'ATIVO'", mas o
-- critério de aceite diz "cupom expirado/inativo não aparece para cliente".
-- Só o status não cobre o caso do cupom ATIVO com data_expiracao no passado,
-- que continuaria visível. Para satisfazer o critério de aceite, "visível"
-- aqui significa utilizável: ATIVO **e** dentro da validade.
-- data_expiracao >= CURRENT_DATE mantém válido o cupom que vence hoje.
CREATE POLICY cupom_select ON cupom FOR SELECT
    USING (current_setting('app.usuario_role', true) = 'ADMIN'
        OR (status = 'ATIVO' AND data_expiracao >= CURRENT_DATE));

-- Cupom é instrumento comercial da loja: só ADMIN emite e administra.
CREATE POLICY cupom_insert ON cupom FOR INSERT
    WITH CHECK (current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY cupom_update ON cupom FOR UPDATE
    USING      (current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY cupom_delete ON cupom FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- ------------------------------------------------------------
-- 7. Policies de avaliacao
-- ------------------------------------------------------------
-- NULLIF(..., '') antes do cast: current_setting devolve string vazia
-- (não NULL) quando o GUC já foi tocado naquele backend, e ''::bigint
-- levanta 22P02. Sem o NULLIF, a sessão anônima receberia erro de sintaxe
-- em vez de simplesmente ter o acesso negado. Ver 04_fix_rls_identidade.sql.

-- Avaliação é parte da vitrine: qualquer sessão lê, inclusive a anônima.
CREATE POLICY avaliacao_select ON avaliacao FOR SELECT
    USING (true);

-- CRITÉRIO DE ACEITE: só dá para assinar avaliação em nome próprio.
CREATE POLICY avaliacao_insert ON avaliacao FOR INSERT
    WITH CHECK (id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

-- USING escolhe quais linhas o autor enxerga para alterar;
-- WITH CHECK impede que ele transfira a autoria da avaliação para outro.
CREATE POLICY avaliacao_update ON avaliacao FOR UPDATE
    USING      (id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
             OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY avaliacao_delete ON avaliacao FOR DELETE
    USING (id_cliente = nullif(current_setting('app.usuario_id', true), '')::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

-- ------------------------------------------------------------
-- 8. Grants
-- ------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE
    ON cupom, avaliacao
    TO app_ecommerce;

GRANT USAGE, SELECT
    ON SEQUENCE seq_cupom_id, seq_avaliacao_id
    TO app_ecommerce;

-- ============================================================
-- 9. Teste de validação (critérios de aceite da issue #9)
--
--    ATENÇÃO: este bloco só tem valor se executado por uma role SEM
--    BYPASSRLS. As policies não se aplicam a quem tem esse atributo, e no
--    Supabase tanto `postgres` quanto `service_role` têm. Rodando o arquivo
--    pelo SQL Editor (que conecta como postgres) o teste é PULADO de
--    propósito — a validação de verdade está em
--    src/test/java/br/edu/ifpb/es/daw/CupomAvaliacaoRlsIntegrationTest.java,
--    que conecta como app_ecommerce.
-- ============================================================
DO $$
DECLARE
    v_cli_a BIGINT; v_cli_b BIGINT; v_vend BIGINT; v_cat BIGINT; v_prod BIGINT;
    v_cup_ok BIGINT; v_cup_inativo BIGINT; v_cup_expirado BIGINT;
    v_aval_a BIGINT; v_aval_b BIGINT;
    n INT;
BEGIN
    IF (SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user) THEN
        RAISE NOTICE '================================================================';
        RAISE NOTICE 'TESTE PULADO: a role % possui BYPASSRLS.', current_user;
        RAISE NOTICE 'As policies nao se aplicam a ela, entao o teste nao provaria nada.';
        RAISE NOTICE 'Para validar a issue #9, rode CupomAvaliacaoRlsIntegrationTest';
        RAISE NOTICE 'ou execute esta secao conectando como app_ecommerce.';
        RAISE NOTICE '================================================================';
        RETURN;
    END IF;

    -- ---------- cenario, montado como ADMIN ----------
    -- app.usuario_id precisa de valor VALIDO desde o inicio: sob o pooler o
    -- GUC e herdado como string vazia entre transacoes. 0 nao corresponde a
    -- usuario nenhum; quem libera aqui e o role ADMIN.
    PERFORM set_config('app.usuario_id', '0', true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);

    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Cliente A #9', 'cliente.a.issue9@exemplo.local', 'hash', 'CLIENTE', 'Cliente')
      RETURNING id INTO v_cli_a;
    INSERT INTO cliente (id, telefone) VALUES (v_cli_a, '(83) 90000-0001');

    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Cliente B #9', 'cliente.b.issue9@exemplo.local', 'hash', 'CLIENTE', 'Cliente')
      RETURNING id INTO v_cli_b;
    INSERT INTO cliente (id, telefone) VALUES (v_cli_b, '(83) 90000-0002');

    INSERT INTO usuario (nome, email, senha_hash, role, dtype)
         VALUES ('Vendedor #9', 'vendedor.issue9@exemplo.local', 'hash', 'VENDEDOR', 'Vendedor')
      RETURNING id INTO v_vend;
    INSERT INTO vendedor (id, razao_social, cnpj_cpf)
         VALUES (v_vend, 'Loja Issue 9 LTDA', '99999999000191');

    INSERT INTO categoria (nome, descricao)
         VALUES ('Categoria Teste #9', 'Removida ao final do teste')
      RETURNING id INTO v_cat;

    INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)
         VALUES ('Produto Avaliado #9', 10, 100.00, v_vend, v_cat)
      RETURNING id_produto INTO v_prod;

    INSERT INTO cupom (codigo, valor_desconto, data_expiracao, status)
         VALUES ('ISSUE9-OK', 10.00, CURRENT_DATE + 30, 'ATIVO')
      RETURNING id INTO v_cup_ok;
    INSERT INTO cupom (codigo, valor_desconto, data_expiracao, status)
         VALUES ('ISSUE9-INATIVO', 10.00, CURRENT_DATE + 30, 'INATIVO')
      RETURNING id INTO v_cup_inativo;
    INSERT INTO cupom (codigo, valor_desconto, data_expiracao, status)
         VALUES ('ISSUE9-EXPIRADO', 10.00, CURRENT_DATE - 1, 'ATIVO')
      RETURNING id INTO v_cup_expirado;
    RAISE NOTICE 'cenario montado: clienteA=%, clienteB=%, produto=%', v_cli_a, v_cli_b, v_prod;

    -- 1) ADMIN enxerga os tres cupons
    SELECT count(*) INTO n FROM cupom
     WHERE id IN (v_cup_ok, v_cup_inativo, v_cup_expirado);
    IF n <> 3 THEN RAISE EXCEPTION 'FALHA: ADMIN deveria ver 3 cupons, viu %', n; END IF;
    RAISE NOTICE 'OK: ADMIN enxerga cupom ativo, inativo e expirado';

    -- ---------- a partir daqui, sessao do CLIENTE A ----------
    PERFORM set_config('app.usuario_role', 'CLIENTE', true);
    PERFORM set_config('app.usuario_id', v_cli_a::text, true);

    -- 2) CRITERIO DE ACEITE: cupom expirado/inativo nao aparece para cliente
    SELECT count(*) INTO n FROM cupom WHERE id = v_cup_inativo;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: cliente enxergou cupom INATIVO'; END IF;
    RAISE NOTICE 'OK: cupom INATIVO invisivel para o cliente';

    SELECT count(*) INTO n FROM cupom WHERE id = v_cup_expirado;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: cliente enxergou cupom EXPIRADO'; END IF;
    RAISE NOTICE 'OK: cupom EXPIRADO invisivel para o cliente';

    SELECT count(*) INTO n FROM cupom WHERE id = v_cup_ok;
    IF n <> 1 THEN RAISE EXCEPTION 'FALHA: cliente nao enxergou cupom ATIVO e valido'; END IF;
    RAISE NOTICE 'OK: cupom ATIVO e dentro da validade visivel para o cliente';

    -- 3) cliente nao emite cupom
    BEGIN
        INSERT INTO cupom (codigo, valor_desconto, data_expiracao)
             VALUES ('ISSUE9-PIRATA', 99.00, CURRENT_DATE + 30);
        RAISE EXCEPTION 'FALHA: CLIENTE conseguiu criar cupom';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: cupom_insert exigiu ADMIN';
    END;

    -- 4) A avalia em nome proprio
    INSERT INTO avaliacao (nota, comentario, id_cliente, id_produto)
         VALUES (5, 'Avaliacao do A', v_cli_a, v_prod) RETURNING id INTO v_aval_a;
    RAISE NOTICE 'OK: cliente A assinou a propria avaliacao (id=%)', v_aval_a;

    -- 5) CRITERIO DE ACEITE: A tenta avaliar assinando como B
    BEGIN
        INSERT INTO avaliacao (nota, comentario, id_cliente, id_produto)
             VALUES (1, 'Avaliacao forjada', v_cli_b, v_prod);
        RAISE EXCEPTION 'FALHA: cliente A assinou avaliacao com o id do cliente B';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: avaliacao_insert bloqueou assinatura em nome de outro cliente';
    END;

    -- 6) CHECK da nota
    BEGIN
        INSERT INTO avaliacao (nota, id_cliente, id_produto) VALUES (6, v_cli_a, v_prod);
        RAISE EXCEPTION 'FALHA: chk_avaliacao_nota aceitou nota 6';
    EXCEPTION WHEN check_violation THEN
        RAISE NOTICE 'OK: chk_avaliacao_nota rejeitou nota fora de 1..5';
    END;

    -- ---------- avaliacao do B, criada como ADMIN ----------
    PERFORM set_config('app.usuario_role', 'ADMIN', true);
    INSERT INTO avaliacao (nota, comentario, id_cliente, id_produto)
         VALUES (3, 'Avaliacao do B', v_cli_b, v_prod) RETURNING id INTO v_aval_b;
    PERFORM set_config('app.usuario_role', 'CLIENTE', true);

    -- 7) leitura de avaliacao e publica: A enxerga a avaliacao de B
    SELECT count(*) INTO n FROM avaliacao WHERE id IN (v_aval_a, v_aval_b);
    IF n <> 2 THEN RAISE EXCEPTION 'FALHA: SELECT publico nao devolveu as 2 avaliacoes (veio %)', n; END IF;
    RAISE NOTICE 'OK: avaliacao_select liberou leitura das duas avaliacoes';

    -- 8) A altera a propria avaliacao -> 1 linha
    UPDATE avaliacao SET nota = 4 WHERE id = v_aval_a;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 1 THEN RAISE EXCEPTION 'FALHA: A nao alterou a propria avaliacao (% linhas)', n; END IF;
    RAISE NOTICE 'OK: A alterou a propria avaliacao';

    -- 9) A tenta alterar a avaliacao de B -> 0 linhas
    UPDATE avaliacao SET nota = 1 WHERE id = v_aval_b;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: UPDATE em avaliacao de outro afetou % linhas', n; END IF;
    RAISE NOTICE 'OK: UPDATE em avaliacao de B afetou 0 linhas';

    -- 10) A tenta TRANSFERIR a autoria da propria avaliacao para B
    BEGIN
        UPDATE avaliacao SET id_cliente = v_cli_b WHERE id = v_aval_a;
        RAISE EXCEPTION 'FALHA: A transferiu a autoria da avaliacao para B';
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'OK: WITH CHECK impediu transferencia de autoria';
    END;

    -- 11) A tenta apagar a avaliacao de B -> 0 linhas
    DELETE FROM avaliacao WHERE id = v_aval_b;
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: DELETE em avaliacao de outro afetou % linhas', n; END IF;
    RAISE NOTICE 'OK: DELETE em avaliacao de B afetou 0 linhas';

    -- ---------- sessao ANONIMA (sem contexto, como o filtro JWT deixa) ----------
    PERFORM set_config('app.usuario_id', '', true);
    PERFORM set_config('app.usuario_role', '', true);

    -- 12) anonimo le avaliacao (vitrine) e o NULLIF evita 22P02
    BEGIN
        SELECT count(*) INTO n FROM avaliacao WHERE id IN (v_aval_a, v_aval_b);
        IF n <> 2 THEN RAISE EXCEPTION 'FALHA: sessao anonima nao leu as avaliacoes (veio %)', n; END IF;
        RAISE NOTICE 'OK: sessao anonima le as avaliacoes';
    EXCEPTION WHEN invalid_text_representation THEN
        RAISE EXCEPTION 'FALHA: cast de string vazia estourou 22P02 - falta NULLIF na policy';
    END;

    -- 13) anonimo nao escreve avaliacao
    BEGIN
        INSERT INTO avaliacao (nota, id_cliente, id_produto) VALUES (5, v_cli_a, v_prod);
        RAISE EXCEPTION 'FALHA: sessao anonima criou avaliacao';
    EXCEPTION
        WHEN insufficient_privilege THEN
            RAISE NOTICE 'OK: sessao anonima bloqueada no INSERT de avaliacao';
        WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'FALHA: cast de string vazia estourou 22P02 - falta NULLIF na policy';
    END;

    -- 14) anonimo tambem nao enxerga cupom inativo/expirado
    SELECT count(*) INTO n FROM cupom WHERE id IN (v_cup_inativo, v_cup_expirado);
    IF n <> 0 THEN RAISE EXCEPTION 'FALHA: sessao anonima enxergou cupom inativo/expirado'; END IF;
    RAISE NOTICE 'OK: sessao anonima nao enxerga cupom inativo/expirado';

    -- ---------- limpeza, de volta como ADMIN ----------
    -- Restaura app.usuario_id valido ANTES de tocar em usuario.
    PERFORM set_config('app.usuario_id', v_cli_a::text, true);
    PERFORM set_config('app.usuario_role', 'ADMIN', true);
    DELETE FROM avaliacao WHERE id IN (v_aval_a, v_aval_b);
    DELETE FROM cupom     WHERE id IN (v_cup_ok, v_cup_inativo, v_cup_expirado);
    DELETE FROM produto   WHERE id_produto = v_prod;
    DELETE FROM categoria WHERE id = v_cat;
    DELETE FROM usuario   WHERE id IN (v_cli_a, v_cli_b, v_vend);

    RAISE NOTICE 'Teste de validacao da issue #9 concluido com sucesso';
END $$;
