package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Produto;

import java.util.List;

public interface ProdutoDAO extends DAO<Produto> {

    /**
     * Lista os produtos de um vendedor.
     *
     * Como produto_select é vitrine pública, qualquer contexto (inclusive o
     * anônimo) consegue listar a loja de qualquer vendedor. O RLS só aperta
     * na escrita.
     *
     * @param idVendedor id do vendedor dono
     * @return lista (possivelmente vazia) de produtos, com Categoria carregada
     */
    List<Produto> findByVendedor(Long idVendedor);

    /**
     * Lista os produtos de uma categoria.
     *
     * @param idCategoria id da categoria
     * @return lista (possivelmente vazia) de produtos, com Categoria carregada
     */
    List<Produto> findByCategoria(Long idCategoria);

    /**
     * Atualiza o estoque de um produto (valor absoluto) e carimba
     * data_atualizacao.
     *
     * O retorno é a forma de enxergar o RLS a partir da aplicação: o
     * produto_update só alcança as linhas do vendedor dono (ou qualquer uma,
     * se ADMIN). Para os demais contextos o UPDATE afeta 0 linhas — não
     * levanta erro. Por isso o retorno é boolean, e não void.
     *
     * @param idProduto  id do produto
     * @param novoEstoque novo valor do estoque (não pode ser negativo:
     *                    a tabela tem CHECK (estoque >= 0))
     * @return true se a linha foi atualizada; false se o produto não existe
     *         ou se o RLS negou o acesso ao contexto atual
     * @throws IllegalArgumentException se novoEstoque for negativo
     */
    boolean atualizarEstoque(Long idProduto, int novoEstoque);

    /**
     * Carrega sob demanda o Vendedor dono do produto (estratégia de carga
     * documentada em ProdutoDAOImpl).
     *
     * Preenche produto.vendedor e devolve o mesmo objeto. O vendedor continua
     * null quando o RLS de vendedor/usuario nega a leitura — o que é o caso
     * normal na vitrine, já que só o próprio vendedor e o ADMIN enxergam
     * esses dados de identidade.
     *
     * @param produto produto já carregado (com idVendedor preenchido)
     * @return o mesmo produto, com o vendedor materializado quando permitido
     */
    Produto carregarVendedor(Produto produto);
}
