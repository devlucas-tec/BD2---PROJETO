package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.dao.impl.ProdutoDAOImpl;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

/**
 * DELETE /produtos (issue #8).
 *
 * produto_delete alcança só as linhas do vendedor dono (ou todas, se ADMIN).
 * Rodado como VENDEDOR, este deleteAll limpa apenas a própria loja — e é
 * assim que deve ser: o "DELETE FROM produto" do DAO já sai filtrado pelo
 * RLS, sem WHERE nenhum na aplicação.
 *
 * Altere ID_USUARIO/ROLE abaixo para ver a diferença entre limpar o catálogo
 * inteiro (ADMIN) e limpar só a própria loja (VENDEDOR).
 */
public class MainProdutoDeleteAll {

    private static final Long ID_USUARIO = 0L;
    private static final String ROLE = "ADMIN";

    public static void main(String[] args) {
        ProdutoDAO produtoDAO = new ProdutoDAOImpl();

        int antes = JwtAuthenticationFilter.executeAnonymous(() -> produtoDAO.findAll().size());
        System.out.println("Produtos na vitrine antes: " + antes);

        JwtAuthenticationFilter.executeAuthenticated(ID_USUARIO, ROLE, produtoDAO::deleteAll);

        int depois = JwtAuthenticationFilter.executeAnonymous(() -> produtoDAO.findAll().size());
        System.out.println("Produtos na vitrine depois (" + ROLE + "): " + depois);
    }
}
