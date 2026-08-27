package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Cupom;

import java.util.List;

public interface CupomDAO extends DAO<Cupom> {

    /**
     * Busca um cupom pelo código (chave natural — cupom.codigo é UNIQUE).
     *
     * ATENÇÃO: o resultado depende de quem está autenticado. A policy
     * cupom_select entrega tudo para ADMIN, mas para os demais papéis (e para
     * a sessão anônima) filtra por ATIVO e dentro da validade. Ou seja, um
     * CLIENTE recebe null para cupom expirado/inativo, enquanto um ADMIN
     * recebe o objeto.
     *
     * Para decidir se um cupom pode ser APLICADO, use findValidoByCodigo:
     * ver a nota sobre validação de expiração em CupomDAOImpl.
     *
     * @param codigo código exato do cupom
     * @return o cupom, ou null se não existir ou o RLS não permitir enxergá-lo
     */
    Cupom findByCodigo(String codigo);

    /**
     * Busca um cupom utilizável pelo código: precisa estar ATIVO e dentro da
     * validade, independentemente do papel de quem consulta.
     *
     * Diferente de findByCodigo, este método aplica o predicado de validade
     * no próprio SQL, então devolve null para cupom expirado/inativo mesmo
     * para um ADMIN. É o método que a regra de negócio deve usar na hora de
     * aplicar um desconto.
     *
     * @param codigo código exato do cupom
     * @return o cupom utilizável, ou null
     */
    Cupom findValidoByCodigo(String codigo);

    /**
     * Lista os cupons utilizáveis (ATIVO e dentro da validade).
     *
     * @return lista possivelmente vazia
     */
    List<Cupom> findValidos();

    /**
     * Diz se o cupom está expirado, comparando data_expiracao com a data
     * CORRENTE DO BANCO (CURRENT_DATE), não com o relógio da aplicação.
     *
     * A distinção importa: o servidor de aplicação e o Postgres podem estar
     * em fusos ou com relógios diferentes, e é a data do banco que as policies
     * de RLS usam. Validar com LocalDate.now() aqui produziria um veredito
     * que não bate com o que o banco enxerga.
     *
     * @param cupom cupom com data_expiracao preenchida
     * @return true se a data de expiração já passou
     */
    boolean isExpirado(Cupom cupom);
}
