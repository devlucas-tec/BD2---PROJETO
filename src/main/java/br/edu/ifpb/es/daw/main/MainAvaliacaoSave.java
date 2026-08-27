package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.*;
import br.edu.ifpb.es.daw.dao.impl.*;
import br.edu.ifpb.es.daw.entities.*;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Critério de aceite da issue #10, lado avaliação.
 *
 * Monta um cenário com dois clientes avaliando o mesmo produto e exercita
 * cada operação pedida pela issue — CRUD, findByProduto, findByCliente e a
 * média de notas — sempre através do JwtAuthenticationFilter, que define o
 * contexto visto pelo RLS. Ao final, remove tudo que criou.
 *
 * Requer .env configurado e os scripts 01..05 aplicados.
 */
public class MainAvaliacaoSave {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    public static void main(String[] args) {
        AvaliacaoDAO avaliacaoDAO = new AvaliacaoDAOImpl();
        ClienteDAO clienteDAO = new ClienteDAOImpl();
        VendedorDAO vendedorDAO = new VendedorDAOImpl();
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl();
        ProdutoDAO produtoDAO = new ProdutoDAOImpl();

        String sufixo = String.valueOf(System.currentTimeMillis());

        Cliente clienteA = novoCliente("Cliente A", sufixo + "1");
        Cliente clienteB = novoCliente("Cliente B", sufixo + "2");
        Vendedor vendedor = novoVendedor(sufixo);
        Categoria categoria = new Categoria();
        categoria.setNome("Categoria Issue 10 " + sufixo);
        Produto produto = new Produto();

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            clienteDAO.save(clienteA);
            clienteDAO.save(clienteB);
            vendedorDAO.save(vendedor);
            categoriaDAO.save(categoria);

            produto.setNome("Produto Avaliado #10");
            produto.setEstoque(10);
            produto.setPreco(new BigDecimal("199.90"));
            produto.setIdVendedor(vendedor.getId());
            produto.setCategoria(categoria);
            produtoDAO.save(produto);
        });
        System.out.println("cenario: clienteA=" + clienteA.getId()
                + ", clienteB=" + clienteB.getId() + ", produto=" + produto.getId());

        try {
            // ---------- POST /avaliacoes — A assina em nome próprio ----------
            Avaliacao deA = nova(5, "Excelente", clienteA.getId(), produto.getId());
            JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                    () -> avaliacaoDAO.save(deA));
            System.out.println("POST /avaliacoes (A) -> id " + deA.getId()
                    + ", data_avaliacao=" + deA.getDataAvaliacao()
                    + "  <- preenchida no INSERT, ja disponivel no objeto");

            // ---------- POST /avaliacoes assinando como outro: RLS recusa ----------
            try {
                Avaliacao forjada = nova(1, "Forjada", clienteB.getId(), produto.getId());
                JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                        () -> avaliacaoDAO.save(forjada));
                System.out.println("POST /avaliacoes (A assinando como B) -> NAO DEVERIA CHEGAR AQUI");
            } catch (RuntimeException e) {
                System.out.println("POST /avaliacoes (A assinando como B) -> recusado pelo RLS (esperado)");
            }

            // ---------- B avalia o mesmo produto ----------
            Avaliacao deB = nova(3, "Razoavel", clienteB.getId(), produto.getId());
            JwtAuthenticationFilter.executeAuthenticated(clienteB.getId(), "CLIENTE",
                    () -> avaliacaoDAO.save(deB));
            System.out.println("POST /avaliacoes (B) -> id " + deB.getId());

            // ---------- GET /produtos/{id}/avaliacoes (anônimo: leitura pública) ----------
            List<Avaliacao> doProduto = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.findByProduto(produto.getId()));
            System.out.println();
            System.out.println("GET /produtos/" + produto.getId() + "/avaliacoes (anonimo) -> "
                    + doProduto.size() + " avaliacao(oes)");
            doProduto.forEach(a -> System.out.println("   " + a));

            // ---------- GET /clientes/{id}/avaliacoes ----------
            List<Avaliacao> doClienteA = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.findByCliente(clienteA.getId()));
            System.out.println("GET /clientes/" + clienteA.getId() + "/avaliacoes -> "
                    + doClienteA.size() + " (esperado: 1)");

            // ---------- Média de notas ----------
            OptionalDouble media = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.mediaNotasPorProduto(produto.getId()));
            int quantas = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.contarPorProduto(produto.getId()));
            System.out.println();
            System.out.println("media de notas -> " + media.getAsDouble()
                    + " em " + quantas + " avaliacoes (esperado: 4.0 em 2)");

            OptionalDouble semAvaliacao = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.mediaNotasPorProduto(-1L));
            System.out.println("media de produto sem avaliacao -> isPresent="
                    + semAvaliacao.isPresent() + " (esperado: false, nao 0.0)");

            // ---------- PUT /avaliacoes/{id} ----------
            deA.setNota(4);
            deA.setComentario("Revisando: muito bom");
            JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                    () -> avaliacaoDAO.update(deA));
            System.out.println();
            System.out.println("PUT /avaliacoes/" + deA.getId() + " (pelo autor) -> "
                    + JwtAuthenticationFilter.executeAnonymous(() -> avaliacaoDAO.findById(deA.getId())));

            // A tenta editar a avaliação de B: o RLS nega em silêncio
            Avaliacao copiaDeB = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.findById(deB.getId()));
            copiaDeB.setNota(1);
            JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                    () -> avaliacaoDAO.update(copiaDeB));
            Avaliacao deBDepois = JwtAuthenticationFilter.executeAnonymous(
                    () -> avaliacaoDAO.findById(deB.getId()));
            System.out.println("PUT /avaliacoes/" + deB.getId() + " (por A) -> nota continua "
                    + deBDepois.getNota() + " (esperado: 3 — RLS nega sem erro)");

            // ---------- DELETE /avaliacoes/{id} ----------
            JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                    () -> avaliacaoDAO.delete(deB));
            System.out.println("DELETE /avaliacoes/" + deB.getId() + " (por A) -> ainda existe? "
                    + (JwtAuthenticationFilter.executeAnonymous(
                            () -> avaliacaoDAO.findById(deB.getId())) != null)
                    + " (esperado: true — nao e dele)");

            JwtAuthenticationFilter.executeAuthenticated(clienteB.getId(), "CLIENTE",
                    () -> avaliacaoDAO.delete(deB));
            System.out.println("DELETE /avaliacoes/" + deB.getId() + " (pelo autor) -> "
                    + JwtAuthenticationFilter.executeAnonymous(() -> avaliacaoDAO.findById(deB.getId()))
                    + " (esperado: null)");

        } finally {
            JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
                // avaliacao tem ON DELETE CASCADE para cliente e produto, mas
                // apagar explicitamente deixa a intencao clara.
                avaliacaoDAO.findByProduto(produto.getId()).forEach(avaliacaoDAO::delete);
                produtoDAO.delete(produto);
                categoriaDAO.delete(categoria);
                clienteDAO.delete(clienteA);
                clienteDAO.delete(clienteB);
                vendedorDAO.delete(vendedor);
            });
            System.out.println("cenario removido");
        }
    }

    private static Avaliacao nova(int nota, String comentario, Long idCliente, Long idProduto) {
        Avaliacao a = new Avaliacao();
        a.setNota(nota);
        a.setComentario(comentario);
        a.setIdCliente(idCliente);
        a.setIdProduto(idProduto);
        return a;
    }

    private static Cliente novoCliente(String nome, String sufixo) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setEmail("issue10." + sufixo + "@exemplo.local");
        c.setSenhaHash("hash");
        c.setTelefone("(83) 90000-0000");
        return c;
    }

    private static Vendedor novoVendedor(String sufixo) {
        Vendedor v = new Vendedor();
        v.setNome("Vendedor Issue 10");
        v.setEmail("vendedor.issue10." + sufixo + "@exemplo.local");
        v.setSenhaHash("hash");
        v.setRazaoSocial("Loja Issue 10 LTDA");
        v.setCnpjCpf(sufixo);
        return v;
    }
}
