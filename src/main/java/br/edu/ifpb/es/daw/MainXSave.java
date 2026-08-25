package br.edu.ifpb.es.daw;

import br.edu.ifpb.es.daw.dao.ClienteDAO;
import br.edu.ifpb.es.daw.dao.impl.ClienteDAOImpl;
import br.edu.ifpb.es.daw.entities.Cliente;

public class MainXSave {

    public static void main(String[] args) {
        ClienteDAO clienteDAO = new ClienteDAOImpl();

        // CREATE — INSERT em usuario + INSERT em cliente (mesma transação)
        Cliente cliente = new Cliente();
        cliente.setNome("Lucas Barbosa");
        cliente.setEmail("lucas.teste@example.com");
        cliente.setSenhaHash("hash_senha_123");  // renomeado de setSenha
        cliente.setTelefone("(83) 99999-9999");

        clienteDAO.save(cliente);
        System.out.println("Cliente salvo com ID: " + cliente.getId());

        // READ
        Cliente buscado = clienteDAO.findById(cliente.getId());
        System.out.println("Cliente encontrado: " + buscado);

        // READ ALL
        System.out.println("Total de clientes: " + clienteDAO.findAll().size());

        // UPDATE
        buscado.setTelefone("(83) 98888-8888");
        clienteDAO.update(buscado);
        System.out.println("Cliente atualizado: " + clienteDAO.findById(buscado.getId()));

        // DELETE
        clienteDAO.delete(buscado);
        System.out.println("Cliente deletado. Buscando novamente: "
                + clienteDAO.findById(buscado.getId()));
    }
}