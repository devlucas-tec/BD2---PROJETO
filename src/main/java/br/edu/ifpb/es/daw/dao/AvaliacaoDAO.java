package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Avaliacao;

import java.util.List;
import java.util.OptionalDouble;

public interface AvaliacaoDAO extends DAO<Avaliacao> {

    /**
     * Lista as avaliações de um produto, da mais recente para a mais antiga.
     *
     * A leitura de avaliacao é pública (avaliacao_select USING (true)), então
     * qualquer contexto — inclusive o anônimo — enxerga a lista completa.
     *
     * @param idProduto id do produto avaliado
     * @return lista possivelmente vazia
     */
    List<Avaliacao> findByProduto(Long idProduto);

    /**
     * Lista as avaliações escritas por um cliente, da mais recente para a
     * mais antiga.
     *
     * Também é leitura pública: o histórico de avaliações de um cliente
     * aparece na vitrine. O que o RLS protege é a ESCRITA (só o autor mexe)
     * e os dados de identidade do cliente, que moram em usuario/cliente e
     * seguem restritos.
     *
     * @param idCliente id do cliente autor
     * @return lista possivelmente vazia
     */
    List<Avaliacao> findByCliente(Long idCliente);

    /**
     * Média das notas de um produto, calculada no banco com AVG.
     *
     * Devolve OptionalDouble em vez de Double justamente para distinguir
     * "produto sem nenhuma avaliação" de "média zero" — AVG sobre conjunto
     * vazio devolve NULL no SQL, e um Double nulo estouraria NPE em qualquer
     * unboxing distraído mais adiante.
     *
     * @param idProduto id do produto
     * @return média das notas, ou vazio se o produto não tem avaliações
     */
    OptionalDouble mediaNotasPorProduto(Long idProduto);

    /**
     * Quantidade de avaliações de um produto — o par natural da média, já que
     * média sem contagem não diz nada sobre confiança.
     *
     * @param idProduto id do produto
     * @return total de avaliações
     */
    int contarPorProduto(Long idProduto);
}
