-- 07_ddl_item_carrinho.sql

CREATE TABLE item_carrinho (
                               carrinho_id     BIGINT        NOT NULL,
                               produto_id      BIGINT        NOT NULL,
                               quantidade      INTEGER       NOT NULL,
                               preco_unitario  NUMERIC(10,2) NOT NULL,

                               CONSTRAINT pk_item_carrinho PRIMARY KEY (carrinho_id, produto_id),

                               CONSTRAINT fk_item_carrinho_carrinho FOREIGN KEY (carrinho_id)
                                   REFERENCES carrinho (id) ON DELETE CASCADE,

                               CONSTRAINT fk_item_carrinho_produto FOREIGN KEY (produto_id)
                                   REFERENCES produto (id_produto),

                               CONSTRAINT chk_item_carrinho_quantidade CHECK (quantidade > 0)
);

CREATE INDEX idx_item_carrinho_produto ON item_carrinho (produto_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON item_carrinho TO app_ecommerce;