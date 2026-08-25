-- ============================================================
-- Issue #4: DDL + RLS das tabelas de identidade
-- ============================================================

-- 1. Sequence
CREATE SEQUENCE seq_usuario_id;

-- 2. Tabela usuario
CREATE TABLE usuario (
    id                BIGINT       NOT NULL DEFAULT nextval('seq_usuario_id'),
    nome              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL UNIQUE,
    senha_hash        VARCHAR(255) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    ativo             BOOLEAN      NOT NULL DEFAULT true,
    data_cadastro     TIMESTAMP    NOT NULL DEFAULT now(),
    data_atualizacao  TIMESTAMP    NOT NULL DEFAULT now(),
    dtype             VARCHAR(31),
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT chk_usuario_role CHECK (role IN ('ADMIN', 'CLIENTE', 'VENDEDOR'))
);

-- 3. Tabela admin
CREATE TABLE admin (
    id   BIGINT NOT NULL,
    CONSTRAINT pk_admin PRIMARY KEY (id),
    CONSTRAINT fk_admin_usuario FOREIGN KEY (id)
        REFERENCES usuario (id) ON DELETE CASCADE
);

-- 4. Tabela cliente
CREATE TABLE cliente (
    id       BIGINT       NOT NULL,
    telefone VARCHAR(20),
    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT fk_cliente_usuario FOREIGN KEY (id)
        REFERENCES usuario (id) ON DELETE CASCADE
);

-- 5. Tabela vendedor
CREATE TABLE vendedor (
    id           BIGINT       NOT NULL,
    razao_social VARCHAR(255) NOT NULL,
    cnpj_cpf     VARCHAR(20)  NOT NULL UNIQUE,
    CONSTRAINT pk_vendedor PRIMARY KEY (id),
    CONSTRAINT fk_vendedor_usuario FOREIGN KEY (id)
        REFERENCES usuario (id) ON DELETE CASCADE
);

-- 6. Ativar RLS
ALTER TABLE usuario  ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin    ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente  ENABLE ROW LEVEL SECURITY;
ALTER TABLE vendedor ENABLE ROW LEVEL SECURITY;

-- 7. Forçar RLS
ALTER TABLE usuario  FORCE ROW LEVEL SECURITY;
ALTER TABLE admin    FORCE ROW LEVEL SECURITY;
ALTER TABLE cliente  FORCE ROW LEVEL SECURITY;
ALTER TABLE vendedor FORCE ROW LEVEL SECURITY;

-- 8. Políticas da tabela usuario
CREATE POLICY usuario_select ON usuario FOR SELECT
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY usuario_update ON usuario FOR UPDATE
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY usuario_insert ON usuario FOR INSERT
    WITH CHECK (true);

CREATE POLICY usuario_delete ON usuario FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- 9. Políticas da tabela admin
CREATE POLICY admin_select ON admin FOR SELECT
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY admin_update ON admin FOR UPDATE
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY admin_insert ON admin FOR INSERT
    WITH CHECK (true);

CREATE POLICY admin_delete ON admin FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- 10. Políticas da tabela cliente
CREATE POLICY cliente_select ON cliente FOR SELECT
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY cliente_update ON cliente FOR UPDATE
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY cliente_insert ON cliente FOR INSERT
    WITH CHECK (true);

CREATE POLICY cliente_delete ON cliente FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- 11. Políticas da tabela vendedor
CREATE POLICY vendedor_select ON vendedor FOR SELECT
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY vendedor_update ON vendedor FOR UPDATE
    USING (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN')
    WITH CHECK (id = current_setting('app.usuario_id', true)::bigint
        OR current_setting('app.usuario_role', true) = 'ADMIN');

CREATE POLICY vendedor_insert ON vendedor FOR INSERT
    WITH CHECK (true);

CREATE POLICY vendedor_delete ON vendedor FOR DELETE
    USING (current_setting('app.usuario_role', true) = 'ADMIN');

-- 12. Grants
GRANT SELECT, INSERT, UPDATE, DELETE
    ON usuario, admin, cliente, vendedor
    TO app_ecommerce;

GRANT USAGE, SELECT
    ON SEQUENCE seq_usuario_id
    TO app_ecommerce;