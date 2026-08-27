package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.dao.VendedorDAO;
import br.edu.ifpb.es.daw.dao.impl.CategoriaDAOImpl;
import br.edu.ifpb.es.daw.dao.impl.ProdutoDAOImpl;
import br.edu.ifpb.es.daw.dao.impl.VendedorDAOImpl;
import br.edu.ifpb.es.daw.entities.Categoria;
import br.edu.ifpb.es.daw.entities.Produto;
import br.edu.ifpb.es.daw.entities.Vendedor;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Critério de aceite da issue #8, lado /produtos.
 *
 * Monta um cenário com dois vendedores e exercita, na ordem, cada operação
 * pedida pela issue — CRUD, findByVendedor, findByCategoria,
 * atualizarEstoque e a carga sob demanda do Vendedor — sempre através do
 * JwtAuthenticationFilter, que é o que define o contexto visto pelo RLS.
 *
 * O ponto da demonstração é que a MESMA chamada de DAO devolve resultados
 * diferentes conforme quem está autenticado. Ao final, remove tudo que criou.
 *
 * Requer .env configurado (ver .env.example) e os scripts 01..04 aplicados.
 */
public class MainProdutoSave {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    public static void main(String[] args) {
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
        ProdutoDAO produtoDAO = new ProdutoDAOImpl();
        VendedorDAO vendedorDAO = new VendedorDAOImpl();

        // Sufixo para não colidir com resíduo de execuções anteriores
        // (usuario.email e vendedor.cnpj_cpf são UNIQUE).
        String sufixo = String.valueOf(System.currentTimeMillis());

        // ============================================================
        // Cenário: 1 categoria + 2 vendedores, montado como ADMIN
        // ============================================================
        Categoria categoria = new Categoria();
        categoria.setNome("Perifericos Issue 8 " + sufixo);
        categoria.setDescricao("Categoria do MainProdutoSave");

        Vendedor vendedorA = novoVendedor("Loja A", sufixo + "1");
        Vendedor vendedorB = novoVendedor("Loja B", sufixo + "2");

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            // INSERT ... RETURNING também passa pela policy de SELECT: sem um
            // contexto autorizado, o id voltaria null.
            categoriaDAO.save(categoria);
            vendedorDAO.save(vendedorA);
            vendedorDAO.save(vendedorB);
        });
        System.out.println("cenario: categoria=" + categoria.getId()
                + ", vendedorA=" + vendedorA.getId() + ", vendedorB=" + vendedorB.getId());

        try {
            // ============================================================
            // POST /produtos — vendedor A cadastra em nome próprio
            // ============================================================
            Produto produto = new Produto();
            produto.setNome("Teclado Mecanico");
            produto.setDescricao("Produto do MainProdutoSave");
            produto.setEstoque(10);
            produto.setPreco(new BigDecimal("249.90"));
            produto.setIdVendedor(vendedorA.getId());
            produto.setCategoria(categoria); // sincroniza idCategoria

            JwtAuthenticationFilter.executeAuthenticated(vendedorA.getId(), "VENDEDOR",
                    () -> produtoDAO.save(produto));
            System.out.println("POST /produtos -> id " + produto.getId());

            // ============================================================
            // GET /produtos/{id} — anônimo. Vitrine é pública e a Categoria
            // vem junto no mesmo SELECT (JOIN único).
            // ============================================================
            Produto daVitrine = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.findById(produto.getId()));
            System.out.println("GET /produtos/" + produto.getId() + " (anonimo) -> " + daVitrine);
            System.out.println("   categoria carregada no RowMapper -> " + daVitrine.getCategoria());

            // ============================================================
            // Carga sob demanda do Vendedor: o RLS de identidade decide
            // ============================================================
            Produto anonimoComVendedor = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.carregarVendedor(produtoDAO.findById(produto.getId())));
            System.out.println("carregarVendedor (anonimo)  -> " + anonimoComVendedor.getVendedor()
                    + "  (esperado: null, vendedor_select nega)");

            Produto donoComVendedor = JwtAuthenticationFilter.executeAuthenticated(
                    vendedorA.getId(), "VENDEDOR",
                    () -> produtoDAO.carregarVendedor(produtoDAO.findById(produto.getId())));
            System.out.println("carregarVendedor (dono)     -> " + donoComVendedor.getVendedor());

            // ============================================================
            // GET /produtos?vendedor= e GET /produtos?categoria=
            // ============================================================
            List<Produto> doVendedorA = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.findByVendedor(vendedorA.getId()));
            System.out.println("GET /produtos?vendedor=" + vendedorA.getId()
                    + " -> " + doVendedorA.size() + " produto(s)");

            List<Produto> doVendedorB = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.findByVendedor(vendedorB.getId()));
            System.out.println("GET /produtos?vendedor=" + vendedorB.getId()
                    + " -> " + doVendedorB.size() + " produto(s) (esperado: 0)");

            List<Produto> daCategoria = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.findByCategoria(categoria.getId()));
            System.out.println("GET /produtos?categoria=" + categoria.getId()
                    + " -> " + daCategoria.size() + " produto(s)");

            // ============================================================
            // PATCH /produtos/{id}/estoque — aqui o RLS aperta
            // ============================================================
            boolean peloDono = JwtAuthenticationFilter.executeAuthenticated(
                    vendedorA.getId(), "VENDEDOR",
                    () -> produtoDAO.atualizarEstoque(produto.getId(), 42));
            System.out.println("PATCH estoque pelo dono      -> " + peloDono + " (esperado: true)");

            boolean porOutroVendedor = JwtAuthenticationFilter.executeAuthenticated(
                    vendedorB.getId(), "VENDEDOR",
                    () -> produtoDAO.atualizarEstoque(produto.getId(), 999));
            System.out.println("PATCH estoque por outro      -> " + porOutroVendedor + " (esperado: false)");

            boolean anonimo = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.atualizarEstoque(produto.getId(), 999));
            System.out.println("PATCH estoque anonimo        -> " + anonimo + " (esperado: false)");

            Integer estoqueFinal = JwtAuthenticationFilter.executeAnonymous(
                    () -> produtoDAO.findById(produto.getId()).getEstoque());
            System.out.println("GET /produtos/" + produto.getId()
                    + " -> estoque = " + estoqueFinal + " (esperado: 42)");

            // ============================================================
            // DELETE /produtos/{id} — pelo dono
            // ============================================================
            JwtAuthenticationFilter.executeAuthenticated(vendedorA.getId(), "VENDEDOR",
                    () -> produtoDAO.delete(produto));
            System.out.println("DELETE /produtos/" + produto.getId() + " -> "
                    + JwtAuthenticationFilter.executeAnonymous(() -> produtoDAO.findById(produto.getId()))
                    + " (esperado: null)");

        } finally {
            // Limpeza do cenário — usuario_delete e categoria_delete exigem ADMIN.
            JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
                vendedorDAO.delete(vendedorA);
                vendedorDAO.delete(vendedorB);
                categoriaDAO.delete(categoria);
            });
            System.out.println("cenario removido");
        }
    }

    private static Vendedor novoVendedor(String razaoSocial, String sufixo) {
        Vendedor v = new Vendedor();
        v.setNome(razaoSocial);
        v.setEmail("issue8." + sufixo + "@exemplo.local");
        v.setSenhaHash("hash");
        v.setRazaoSocial(razaoSocial + " LTDA");
        v.setCnpjCpf(sufixo);
        return v;
    }
}
