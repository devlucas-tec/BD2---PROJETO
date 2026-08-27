package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.impl.CategoriaDAOImpl;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

/**
 * DELETE /categorias (issue #8).
 *
 * categoria_delete exige app.usuario_role = ADMIN. Rodar isto sem contexto
 * (ou como VENDEDOR/CLIENTE) não dá erro: o RLS filtra as linhas e o DELETE
 * afeta 0. Por isso o main assume explicitamente o papel de ADMIN.
 *
 * A FK produto -> categoria é ON DELETE RESTRICT, então produtos precisam
 * ser removidos antes (MainProdutoDeleteAll).
 */
public class MainCategoriaDeleteAll {

    private static final Long ID_ADMIN = 0L;

    public static void main(String[] args) {
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl();

        int antes = JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findAll().size());
        System.out.println("Categorias antes: " + antes);

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", categoriaDAO::deleteAll);

        int depois = JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findAll().size());
        System.out.println("Categorias depois: " + depois);
    }
}
