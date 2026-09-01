-- 06_ddl_carrinho.sql

CREATE SEQUENCE seq_carrinho_id;

CREATE TABLE carrinho (
                          id                       BIGINT    NOT NULL DEFAULT nextval('seq_carrinho_id'),
                          data_criacao             TIMESTAMP NOT NULL DEFAULT now(),
                          data_ultima_atualizacao  TIMESTAMP NOT NULL DEFAULT now(),
                          id_cliente               BIGINT    NOT NULL,
                          CONSTRAINT pk_carrinho PRIMARY KEY (id),
                          CONSTRAINT fk_carrinho_cliente FOREIGN KEY (id_cliente)
                              REFERENCES usuario (id),
                          CONSTRAINT uq_carrinho_cliente UNIQUE (id_cliente)
);

CREATE INDEX idx_carrinho_cliente ON carrinho (id_cliente);

GRANT SELECT, INSERT, UPDATE, DELETE ON carrinho TO app_ecommerce;
GRANT USAGE, SELECT ON SEQUENCE seq_carrinho_id TO app_ecommerce;