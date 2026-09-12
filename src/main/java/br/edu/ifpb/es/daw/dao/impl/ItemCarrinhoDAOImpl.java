package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ItemCarrinhoDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.ItemCarrinho;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DAO JDBC de item_carrinho (issue #13). Tabela e policies vêm da issue #12
 * (09_rls_carrinho_item_carrinho.sql).
 *
 * Não estende AbstractDAOImpl&lt;ItemCarrinho&gt; porque ItemCarrinhoDAO não
 * é um DAO&lt;T&gt; genérico — ver o javadoc da interface. A classe ainda
 * usa TransactionalDataAccess normalmente: o contrato "nenhum DAO abre
 * Connection direto" vale para qualquer DAO, com ou sem PK simples.
 */
public class ItemCarrinhoDAOImpl implements ItemCarrinhoDAO {

    private static final RowMapper<ItemCarrinho> ITEM_MAPPER = rs -> {
        ItemCarrinho item = new ItemCarrinho();
        item.setCarrinhoId(rs.getLong("carrinho_id"));
        item.setProdutoId(rs.getLong("produto_id"));
        item.setQuantidade(rs.getInt("quantidade"));
        item.setPrecoUnitario(rs.getBigDecimal("preco_unitario"));
        return item;
    };

    private static final String COLUNAS = "carrinho_id, produto_id, quantidade, preco_unitario";

    // ON CONFLICT casa com pk_item_carrinho (carrinho_id, produto_id):
    // se o produto já está no carrinho, soma a quantidade em vez de
    // duplicar a linha, e atualiza o preço para o valor informado agora.
    private static final String ADICIONAR_ITEM_SQL = """
            INSERT INTO item_carrinho (carrinho_id, produto_id, quantidade, preco_unitario)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (carrinho_id, produto_id)
            DO UPDATE SET
                quantidade = item_carrinho.quantidade + EXCLUDED.quantidade,
                preco_unitario = EXCLUDED.preco_unitario
            """;

    private static final String REMOVER_ITEM_SQL =
            "DELETE FROM item_carrinho WHERE carrinho_id = ? AND produto_id = ?";

    private static final String LISTAR_POR_CARRINHO_SQL =
            "SELECT " + COLUNAS + " FROM item_carrinho WHERE carrinho_id = ? ORDER BY produto_id";

    private static final String LIMPAR_CARRINHO_SQL =
            "DELETE FROM item_carrinho WHERE carrinho_id = ?";

    @Override
    public void adicionarItem(ItemCarrinho item) {
        Objects.requireNonNull(item.getCarrinhoId(), "carrinhoId é obrigatório");
        Objects.requireNonNull(item.getProdutoId(), "produtoId é obrigatório");
        if (item.getQuantidade() == null || item.getQuantidade() <= 0) {
            // Mesmo predicado de chk_item_carrinho_quantidade — falhar aqui
            // dá uma mensagem melhor do que esperar o CHECK do banco estourar.
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(ADICIONAR_ITEM_SQL)) {
                stmt.setLong(1, item.getCarrinhoId());
                stmt.setLong(2, item.getProdutoId());
                stmt.setInt(3, item.getQuantidade());
                stmt.setBigDecimal(4, item.getPrecoUnitario());
                stmt.executeUpdate();
                // Sem RETURNING/linha afetada visível aqui: se o RLS negar
                // (carrinho não é do usuário autenticado), tanto o INSERT
                // quanto o caminho do ON CONFLICT são bloqueados pelo
                // WITH CHECK de item_carrinho_insert e nada é gravado.
            }
        });
    }

    @Override
    public void removerItem(Long carrinhoId, Long produtoId) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(REMOVER_ITEM_SQL)) {
                stmt.setLong(1, carrinhoId);
                stmt.setLong(2, produtoId);
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public List<ItemCarrinho> listarPorCarrinho(Long carrinhoId) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<ItemCarrinho> itens = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(LISTAR_POR_CARRINHO_SQL)) {
                stmt.setLong(1, carrinhoId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        itens.add(ITEM_MAPPER.mapRow(rs));
                    }
                }
            }
            return itens;
        });
    }

    @Override
    public void limparCarrinho(Long carrinhoId) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(LIMPAR_CARRINHO_SQL)) {
                stmt.setLong(1, carrinhoId);
                stmt.executeUpdate();
            }
        });
    }
}
