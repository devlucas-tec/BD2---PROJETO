package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.ItemCarrinho;

import java.util.List;

/**
 * DAO de item_carrinho (issue #13).
 *
 * Não estende DAO&lt;ItemCarrinho&gt;: esse contrato genérico pressupõe uma
 * PK simples (findById(Long id)), e item_carrinho não tem coluna "id" — a
 * PK é composta (carrinho_id, produto_id), ver 07_ddl_item_carrinho.sql.
 * Antes da issue #2 essa chave era um @EmbeddedId; sem JPA, o par é tratado
 * manualmente aqui e em cada SQL do impl (ON CONFLICT / WHERE carrinho_id =
 * ? AND produto_id = ?).
 */
public interface ItemCarrinhoDAO {

    /**
     * Adiciona um item ao carrinho. Se o produto já estiver no carrinho
     * (mesma PK carrinho_id + produto_id), incrementa a quantidade existente
     * em vez de duplicar a linha — via ON CONFLICT DO UPDATE, então a
     * checagem é atômica no próprio banco (sem race entre um SELECT e um
     * INSERT feitos em dois passos pela aplicação).
     *
     * O preço unitário gravado é sempre o informado nesta chamada (reflete
     * o preço do produto no momento em que foi adicionado/reforçado), tanto
     * no INSERT quanto no UPDATE do conflito.
     *
     * @param item carrinhoId, produtoId, quantidade (a ser somada) e
     *             precoUnitario devem estar preenchidos
     */
    void adicionarItem(ItemCarrinho item);

    /**
     * Remove um item do carrinho por completo (a linha inteira), não apenas
     * decrementa a quantidade.
     *
     * @param carrinhoId id do carrinho
     * @param produtoId  id do produto a remover
     */
    void removerItem(Long carrinhoId, Long produtoId);

    /**
     * Lista os itens de um carrinho.
     *
     * @param carrinhoId id do carrinho
     * @return lista possivelmente vazia, ordenada por produto_id
     */
    List<ItemCarrinho> listarPorCarrinho(Long carrinhoId);

    /**
     * Remove todos os itens de um carrinho (esvazia o carrinho sem apagar
     * o carrinho em si).
     *
     * @param carrinhoId id do carrinho a esvaziar
     */
    void limparCarrinho(Long carrinhoId);
}
