package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Carrinho;

public interface CarrinhoDAO extends DAO<Carrinho> {

    /**
     * Busca o carrinho do cliente (relação 1:1 — uq_carrinho_cliente).
     *
     * @param idCliente id do usuário dono do carrinho
     * @return o carrinho, ou null se o cliente ainda não tem um (ou se o
     *         RLS não permitir enxergá-lo no contexto atual)
     */
    Carrinho findByCliente(Long idCliente);

    /**
     * Garante um carrinho para o cliente: devolve o carrinho já existente
     * (via findByCliente) ou cria um novo caso ainda não exista.
     *
     * Operação idempotente — chamar de novo para o mesmo cliente não cria
     * um segundo carrinho (uq_carrinho_cliente barra isso no banco; aqui a
     * checagem evita até tentar o INSERT nas chamadas normais).
     *
     * @param idCliente id do usuário para quem o carrinho será criado
     * @return o carrinho do cliente (novo ou pré-existente)
     */
    Carrinho criarParaCliente(Long idCliente);

    /**
     * Atualiza data_ultima_atualizacao do carrinho para o instante atual.
     * Deve ser chamado sempre que os itens do carrinho mudarem (adicionar,
     * remover, limpar), para que o carrinho reflita quando foi mexido pela
     * última vez.
     *
     * @param idCarrinho id do carrinho a "tocar"
     */
    void atualizarDataAtualizacao(Long idCarrinho);
}
