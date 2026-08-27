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
5. `src/main/sql/05_ddl_rls_cupom_avaliacao.sql`
   * Cria sequências, tabelas e policies de `cupom` e `avaliacao` (catálogo restrito de cupons; avaliações de leitura pública assinadas pelo autor) e concede permissões.

> **⚠️ Sobre os blocos de validação:** os scripts terminam com um bloco `DO $$` que testa as próprias policies. Ele é **pulado de propósito** quando executado pelo SQL Editor, porque o usuário `postgres` do Supabase tem `BYPASSRLS` e as policies nem chegam a ser avaliadas para ele. A validação efetiva do RLS está nos testes de integração, que conectam como `app_ecommerce` (ver seção de Testes).

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
| **`cupom`** | `SELECT` | `ADMIN` vê tudo; demais papéis (e anônimo) só veem cupom **utilizável**: `status = 'ATIVO'` **e** `data_expiracao >= CURRENT_DATE`. |
| **`cupom`** | `INSERT` / `UPDATE` / `DELETE` | Exclusivo para usuários com papel `ADMIN`. |
| **`avaliacao`** | `SELECT` | Público (faz parte da vitrine do produto). |
| **`avaliacao`** | `INSERT` | Só em nome próprio (`id_cliente = app.usuario_id`) ou `ADMIN`. |
| **`avaliacao`** | `UPDATE` / `DELETE` | Exclusivo do autor (`id_cliente = app.usuario_id`) ou `ADMIN`. |

> **Por que `cupom` checa a data e não só o status:** a tarefa da issue #9 fala em "só veem `status = 'ATIVO'`", mas o critério de aceite exige que **cupom expirado** também não apareça. Um cupom `ATIVO` com `data_expiracao` no passado passaria pelo filtro de status e continuaria visível — por isso a policy exige as duas condições.

---

## 📦 Camada de Catálogo (`categoria`, `produto`, `cupom`, `avaliacao`)

Os DAOs de catálogo seguem o mesmo contrato dos demais: todo SQL passa por `TransactionalDataAccess`, então cada operação carrega o contexto RLS da requisição.

### Operações disponíveis

| DAO | Método | Observação |
| :--- | :--- | :--- |
| `CategoriaDAO` | CRUD (`save`, `findById`, `findAll`, `update`, `delete`, `deleteAll`) | Leitura pública; escrita exige `ADMIN`. |
| `CategoriaDAO` | `findByNome(String)` | Busca pela chave natural (`categoria.nome` é `UNIQUE`). |
| `ProdutoDAO` | CRUD | Leitura pública; escrita restrita ao vendedor dono ou `ADMIN`. |
| `ProdutoDAO` | `findByVendedor(Long)` / `findByCategoria(Long)` | Filtram pela FK, com a `Categoria` já carregada. |
| `ProdutoDAO` | `atualizarEstoque(Long, int)` | Devolve `boolean`: `false` quando o RLS nega ou o produto não existe. |
| `ProdutoDAO` | `carregarVendedor(Produto)` | Materializa o `Vendedor` sob demanda (ver abaixo). |
| `CupomDAO` | CRUD | Escrita exige `ADMIN`; leitura filtrada pelo RLS (ver tabela de policies). |
| `CupomDAO` | `findByCodigo(String)` | Chave natural (`cupom.codigo` é `UNIQUE`). O resultado **depende do papel**. |
| `CupomDAO` | `findValidoByCodigo(String)` / `findValidos()` | Só cupom utilizável, **independentemente do papel** (ver abaixo). |
| `CupomDAO` | `isExpirado(Cupom)` | Compara com `CURRENT_DATE` — a data do banco, não a da aplicação. |
| `AvaliacaoDAO` | CRUD | Leitura pública; escrita só do autor ou `ADMIN`. |
| `AvaliacaoDAO` | `findByProduto(Long)` / `findByCliente(Long)` | Filtram pela FK, mais recentes primeiro. |
| `AvaliacaoDAO` | `mediaNotasPorProduto(Long)` | `OptionalDouble` — vazio distingue "sem avaliações" de "média zero". |
| `AvaliacaoDAO` | `contarPorProduto(Long)` | Par natural da média: média sem contagem não diz nada sobre confiança. |

> `atualizarEstoque` devolve `boolean` justamente porque o RLS nega **em silêncio** no `UPDATE`: quando a linha não é do vendedor autenticado, o comando afeta 0 linhas em vez de levantar erro. O row count é o que expõe isso para a aplicação.

### Validação de cupom: por que o RLS não basta

A policy `cupom_select` já esconde cupom expirado/inativo de quem não é `ADMIN`. É tentador concluir que "se `findByCodigo` devolveu algo, o cupom vale" — e isso está errado por dois motivos:

1. **Para `ADMIN` a policy é `USING (true)`**: ele enxerga cupom vencido normalmente. Uma rotina administrativa que aplicasse desconto via `findByCodigo` aceitaria cupom expirado sem reclamar.
2. **RLS é controle de acesso, não regra de negócio.** Amarrar a validade do cupom à visibilidade faz com que qualquer ajuste futuro na policy mude silenciosamente o comportamento do checkout.

Por isso a validade é explícita e independente do papel: `findValidoByCodigo` e `findValidos` aplicam `status = 'ATIVO' AND data_expiracao >= CURRENT_DATE` no próprio SQL. É o método que a regra de negócio deve usar na hora de aplicar um desconto.

Todos usam `CURRENT_DATE` — a data do **banco**, não `LocalDate.now()`. O servidor de aplicação e o Postgres podem divergir em fuso ou em drift de relógio, e é a data do banco que as policies enxergam.

### `data_avaliacao` é preenchida explicitamente no `INSERT`

A coluna tem `DEFAULT now()`, então omiti-la funcionaria. `AvaliacaoDAOImpl` passa o valor mesmo assim, por dois motivos:

* `now()` é o relógio do **servidor de banco**. Deixar o default decidir espalha duas fontes de tempo pelo sistema (o Postgres aqui, `LocalDateTime.now()` em `Produto` e `Usuario`), e elas divergem em fuso e em drift.
* O objeto em memória ficaria com `dataAvaliacao` nulo depois do `save`, já que o valor teria nascido no banco. Quem chamou precisaria de um `findById` só para saber quando a própria avaliação foi criada.

### Estratégia de carga do `RowMapper` de `Produto`

A escolha entre **JOIN único** e **carga sob demanda** não é a mesma para as duas associações — quem decide é o RLS, não a preferência de estilo:

* **`Categoria` → JOIN único (eager, sempre).** A policy `categoria_select` é `USING (true)` e `produto.id_categoria` é `NOT NULL` com FK `ON DELETE RESTRICT`. Somando as duas coisas, o `INNER JOIN` nunca descarta um produto nem devolve categoria vazia, em nenhum contexto de tenant. Custo: três colunas a mais por linha, contra uma query extra por produto (N+1) se fosse sob demanda — o JOIN ganha com folga na listagem de vitrine, que é o caso de uso dominante.

* **`Vendedor` → carga sob demanda (lazy).** Os dados do vendedor moram em `vendedor JOIN usuario`, ambas sob `FORCE ROW LEVEL SECURITY` com policy restritiva (`id = app.usuario_id OR app.usuario_role = 'ADMIN'`). Um `INNER JOIN` até `usuario` dentro do `findAll()` seria **filtrado pelo RLS e zeraria a vitrine**: a sessão anônima e o vendedor concorrente passariam a enxergar zero produtos, e o catálogo público deixaria de existir. Trocar por `LEFT JOIN` devolveria as linhas, mas com todas as colunas do vendedor nulas — pagando o custo do JOIN para não trazer dado nenhum na maioria das requisições. Por isso o vendedor fica fora do `SELECT` do catálogo e é materializado por `carregarVendedor()`, que reusa o `VendedorDAO` e volta a passar pelo RLS.

Contrapartida assumida: `carregarVendedor()` aplicado a uma lista é N+1. É aceitável porque o caminho quente (vitrine) não carrega vendedor, e o caminho que carrega (painel da loja) opera sobre os produtos de um único vendedor. Se virar gargalo, a saída é uma carga em lote (`id IN (...)`), não trocar a estratégia por JOIN.

A FK crua (`Produto.idVendedor`) está sempre preenchida, então nada no domínio depende de o objeto `Vendedor` ter sido materializado.

### Executando os cenários de catálogo

O projeto não tem camada HTTP desde a issue #2 (remoção de Spring/JPA). O equivalente a "o endpoint responde" são as classes de `main/`: cada bloco é a requisição que um controller faria, executada pelo `JwtAuthenticationFilter` — que é quem preenche o `TenantContext` e, portanto, quem determina o que o RLS vai permitir.

Empacote uma vez (o `maven-shade-plugin` já inclui driver e dotenv no jar):

```bash
./mvnw -q clean package -DskipTests
```

E rode o cenário desejado:

```bash
java -cp target/crud-jpa-template-0.0.1-SNAPSHOT.jar br.edu.ifpb.es.daw.main.MainCategoriaSave
```

```bash
java -cp target/crud-jpa-template-0.0.1-SNAPSHOT.jar br.edu.ifpb.es.daw.main.MainProdutoSave
```

```bash
java -cp target/crud-jpa-template-0.0.1-SNAPSHOT.jar br.edu.ifpb.es.daw.main.MainCupomSave
```

```bash
java -cp target/crud-jpa-template-0.0.1-SNAPSHOT.jar br.edu.ifpb.es.daw.main.MainAvaliacaoSave
```

Cada um monta o próprio cenário, exercita as operações sob identidades diferentes — dono, terceiro e anônimo — e remove tudo que criou ao final. `MainCupomSave` é o que mostra na prática a diferença entre `findByCodigo` (varia com o papel) e `findValidoByCodigo` (não varia).

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

Os testes conectam no banco como `app_ecommerce` — uma role **sem** `BYPASSRLS`. Isso é o que dá valor às asserções: se a role tivesse `BYPASSRLS`, as policies não seriam avaliadas e os testes que afirmam *negativa* de acesso passariam por engano.

| Classe | Cobre |
| :--- | :--- |
| `RlsContextIntegrationTest` | Propagação de `app.usuario_id` / `app.usuario_role` por transação (issue #3). |
| `CatalogoDaoIntegrationTest` | DAOs de `categoria` e `produto` e a estratégia de carga do `RowMapper` (issue #8). |
| `CupomAvaliacaoRlsIntegrationTest` | Policies de `cupom` e `avaliacao` em SQL cru (issue #9), incluindo os dois critérios de aceite. |
| `CupomAvaliacaoDaoIntegrationTest` | DAOs de `cupom` e `avaliacao` (issue #10): mapeamento, agregação, carimbo de data e comportamento sob cada identidade. |

Cada classe é **pulada** (não quebrada) quando falta o `.env` na raiz ou quando as tabelas que ela testa ainda não foram criadas.

> ⚠️ **Mergear o PR não aplica o DDL.** Os scripts de `src/main/sql/` precisam ser executados no SQL Editor do Supabase. Enquanto isso não acontece, os testes das tabelas correspondentes ficam pulados e o build passa verde sem ter verificado nada — confira a contagem de testes, não só o `BUILD SUCCESS`.

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