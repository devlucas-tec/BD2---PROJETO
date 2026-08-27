package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Categoria;

public interface CategoriaDAO extends DAO<Categoria> {

    /**
     * Busca uma categoria pelo nome (chave natural — categoria.nome é UNIQUE).
     *
     * A leitura de categoria é vitrine pública no RLS (categoria_select
     * USING (true)), então este método devolve o mesmo resultado para
     * qualquer contexto, inclusive o anônimo.
     *
     * @param nome nome exato da categoria
     * @return a categoria, ou null se não existir
     */
    Categoria findByNome(String nome);
}
