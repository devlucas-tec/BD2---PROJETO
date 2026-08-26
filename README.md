# 🗄️ Banco de Dados II - Persistência com JDBC Puro & Row Level Security (RLS)

Este repositório contém o projeto prático da disciplina de **Banco de Dados II** do curso superior de Tecnologia em **Análise e Desenvolvimento de Sistemas (ADS)** do **IFPB**.

O objetivo central do projeto é a persistência e gerenciamento de dados utilizando **JDBC puro** integrado a um banco de dados **PostgreSQL (Supabase)**, implementando controle de acesso e isolamento de dados com **Row Level Security (RLS)** e gerenciamento transacional de contexto de sessão/tenant.

---

## 📖 Sobre o Projeto

O projeto foi migrado para uma arquitetura baseada em **JDBC nativo**, eliminando frameworks ORM/JPA para proporcionar controle total sobre as operações SQL, transações e segurança no banco. 

### Principais Destaques:
* **Isolamento via RLS (Row Level Security):** Segurança imposta no próprio banco PostgreSQL através de políticas (`CREATE POLICY`) com verificação de identidade e perfil (`app.usuario_id` e `app.usuario_role`).
* **Gerenciamento Transacional Estrito (`TransactionalDataAccess`):** Centralização de abertura de conexões, desativação de `autoCommit`, injeção das variáveis de sessão no escopo `LOCAL` (transacional), execução de queries, `commit`/`rollback` e fechamento de conexões.
* **Compatibilidade com Connection Pooling (Supabase / Supavisor / PgBouncer):** Configuração com `prepareThreshold=0` e tratamento de strings vazias com `NULLIF(..., '')` nas policies para suportar pooler em *transaction mode* na porta `6543`.
* **Desacoplamento por DAOs:** Mapeamento explícito de linhas do `ResultSet` com interfaces funcionais (`RowMapper`).

---

## 🛠️ Tecnologias Utilizadas

* **Linguagem:** Java 17+
* **Acesso a Dados:** JDBC Puro (`org.postgresql:postgresql:42.7.10`)
* **Banco de Dados:** PostgreSQL 15+ hospedado no [Supabase](https://supabase.com/)
* **Pooler / Proxy de Conexão:** PgBouncer / Supavisor (Porta 6543 - Transaction Mode)
* **Gerenciamento de Configurações:** Dotenv Java (`io.github.cdimascio:dotenv-java:3.2.0`)
* **Testes Automatizados:** JUnit 5 (`org.junit.jupiter:junit-jupiter:5.10.2`)
* **Build Tool:** Apache Maven

---

## 🏗️ Arquitetura e Estrutura do Projeto

```
src/
├── main/
│   ├── java/br/edu/ifpb/es/daw/
│   │   ├── context/          # Gerenciamento de contexto da thread (TenantContext)
│   │   ├── dao/              # Interfaces DAO, RowMapper e TransactionalDataAccess
│   │   │   └── impl/         # Implementações JDBC dos DAOs (ProdutoDAOImpl, etc.)
│   │   ├── entities/         # Classes de modelo / domínio (Usuario, Produto, etc.)
│   │   ├── filter/           # Simulação/execução autenticada (JwtAuthenticationFilter)
│   │   ├── main/             # Classes executáveis de teste manual
│   │   └── util/             # Utilitários de conexão (DatabaseConnection)
│   └── sql/                  # Scripts DDL, RLS, Grants e correções no PostgreSQL
└── test/
    └── java/br/edu/ifpb/es/daw/  # Testes de integração (ex: RlsContextIntegrationTest)
```

### 1. Camada de Persistência e Transação
* **`DatabaseConnection`:** Gerencia a criação de conexões JDBC lendo variáveis do arquivo `.env` e assegurando o parâmetro `prepareThreshold=0`.
* **`TransactionalDataAccess`:** Garante que toda operação execute dentro de uma transação explícita e propague o contexto RLS antes de qualquer comando SQL via `set_config('app.usuario_id', ?, true)` e `set_config('app.usuario_role', ?, true)`.
* **`AbstractDAOImpl` & DAOs:** Implementações que recebem o callback da conexão transacional e executam comandos SQL preparados (`PreparedStatement`).

### 2. Contexto de Sessão e Autenticação
* **`TenantContext`:** Armazena a identidade do usuário autenticado (`usuarioId` e `role`) em uma variável `ThreadLocal`.
* **`JwtAuthenticationFilter`:** Interceptador que preenche o contexto antes da execução da operação e garante sua limpeza (`clear()`) no bloco `finally`.

---

## 🔧 Configuração do Ambiente e Banco de Dados (Supabase)

### 1. Criar Role da Aplicação
No **SQL Editor** do seu projeto no Supabase, crie o papel `app_ecommerce` que será utilizado pela aplicação:

```sql
CREATE ROLE app_ecommerce WITH LOGIN PASSWORD 'sua_senha_aqui' NOSUPERUSER NOCREATEDB NOCREATEROLE;
```

### 2. Executar os Scripts SQL
Execute os scripts no **SQL Editor** rigorosamente na ordem numérica:

1. `src/main/sql/01_grants_app_ecommerce.sql`
   * Cria tabelas de identidade (`usuario`, `admin`, `cliente`, `vendedor`), ativa e força RLS, cria policies e concede permissões (GRANTs).
2. `src/main/sql/02_ddl_categoria_produto.sql`
   * Cria sequências e tabelas de catálogo (`categoria` e `produto`), índices de chaves estrangeiras e permissões.
3. `src/main/sql/03_rls_categoria_produto.sql`
   * Define as políticas de RLS para catálogo (leitura pública / vitrine; escrita restrita a vendedores donos ou admins).
4. `src/main/sql/04_fix_rls_identidade.sql`
   * Aplica correção de compatibilidade com o pooler do Supabase utilizando `NULLIF(..., '')` nas políticas de identidade.

### 3. Configurar o Arquivo `.env`
Copie o template `.env.example` para `.env` na raiz do projeto:

```bash
cp .env.example .env
```

Preencha com as credenciais do seu banco (utilize a string de conexão do pooler na porta **6543**):

```env
DB_URL=jdbc:postgresql://aws-0-sa-east-1.pooler.supabase.com:6543/postgres?prepareThreshold=0
DB_USER=app_ecommerce
DB_PASSWORD=sua_senha_aqui
```

> **⚠️ Importante:** O parâmetro `prepareThreshold=0` na URL de conexão é obrigatório para evitar conflitos de prepared statements reutilizados pelo PgBouncer em modo de transação.

---

## 🛡️ Políticas de Segurança (Row Level Security - RLS)

| Tabela | Operação | Regra de Acesso |
| :--- | :--- | :--- |
| **`usuario` / `cliente` / `vendedor` / `admin`** | `SELECT` / `UPDATE` | O próprio usuário (`id = app.usuario_id`) ou `ADMIN`. |
| **`usuario` / `cliente` / `vendedor` / `admin`** | `INSERT` | Público (auto-cadastro) ou via transação controlada. |
| **`usuario` / `cliente` / `vendedor` / `admin`** | `DELETE` | Exclusivo para usuários com papel `ADMIN`. |
| **`categoria`** | `SELECT` | Público (vitrine acessível por qualquer sessão/anônimo). |
| **`categoria`** | `INSERT` / `UPDATE` / `DELETE` | Exclusivo para usuários com papel `ADMIN`. |
| **`produto`** | `SELECT` | Público (vitrine acessível por qualquer sessão/anônimo). |
| **`produto`** | `INSERT` / `UPDATE` / `DELETE` | Exclusivo do vendedor dono (`id_vendedor = app.usuario_id`) ou `ADMIN`. |

---

## 🧪 Testes e Compilação

Para compilar o projeto:
```bash
./mvnw clean compile
```

Para rodar os testes de integração de contexto e RLS:
```bash
./mvnw test
```

---

## 🗺️ Modelo de Dados Conceitual

O modelo conceitual do e-commerce foi concebido na disciplina de Banco de Dados I e serve como base para as entidades e tabelas implementadas:

![Modelo Conceitual](./modelo-conceitual.png)

* [🔗 Visualizar diagrama original no editor brModelo Web](https://app.brmodeloweb.com/#!/publicview/69a720f96431b763534360b3)

---

## 👥 Equipe do Projeto

* **Lucas Barbosa**
* **Paulo Moura**
* **Valdênio Pantaleão**

Estudantes de Análise e Desenvolvimento de Sistemas (ADS) — **IFPB**