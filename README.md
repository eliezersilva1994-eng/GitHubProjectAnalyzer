# GitHub Project Analyzer

Ferramenta de linha de comando que analisa qualquer repositório público do GitHub e gera um relatório com estatísticas do projeto: commits, contribuidores, issues, linguagens utilizadas, atividade semanal e evolução ao longo do tempo. Os dados são exportados para um arquivo `dados.json`, que alimenta um dashboard visual (`dashboard.html`) com gráficos. Cada execução também fica registrada num banco SQLite local, permitindo acompanhar a evolução do repositório entre análises.

## Funcionalidades

- Dados gerais do repositório (nome, estrelas, linguagem principal)
- Total de contribuidores, com ranking individual (login + total de commits de cada um)
- Total de commits (via GitHub Stats API)
- Issues abertas e fechadas, contadas separadamente (excluindo pull requests)
- Percentual de uso de cada linguagem no projeto
- Atividade de commits nas últimas 52 semanas
- Histórico de análises persistido em SQLite, com gráfico de evolução do projeto (commits e issues ao longo das execuções)
- Dashboard visual com gráficos (Chart.js), lendo o JSON gerado
- Tratamento de casos extremos da API do GitHub: repositórios sem linguagem definida, estatísticas ainda não calculadas pelo GitHub (comum em repositórios novos) e limites de paginação em repositórios muito grandes

## Tecnologias

- **Java 17+** com `java.net.http.HttpClient` (sem dependências externas de HTTP)
- **org.json** para manipulação de JSON
- **SQLite** (via driver JDBC `sqlite-jdbc`) para persistir o histórico de análises
- **Maven** para build e gerenciamento de dependências
- **HTML + JavaScript + Chart.js** para o dashboard visual
- **GitHub REST API** como fonte de dados

## Pré-requisitos

- Java 17 ou superior
- Maven
- Um [token de acesso pessoal do GitHub](https://github.com/settings/tokens) (classic), com o escopo `public_repo`

## Configuração

1. Clone este repositório:
   ```bash
   git clone https://github.com/eliezersilva1994eng/GitHubProjectAnalyzer.git
   cd GitHubProjectAnalyzer
   ```

2. Gere um token de acesso pessoal no GitHub (Settings → Developer settings → Personal access tokens → Tokens (classic)), com o escopo `public_repo`.

3. Defina a variável de ambiente `GITHUB_TOKEN` com o valor do seu token:

   **Windows (PowerShell):**
   ```powershell
   $env:GITHUB_TOKEN="seu_token_aqui"
   ```

   **Linux/Mac:**
   ```bash
   export GITHUB_TOKEN="seu_token_aqui"
   ```

   > Se estiver rodando pela IntelliJ, defina a variável direto na Run Configuration (Run → Edit Configurations → Environment variables), assim ela não se perde entre execuções.

## Como usar

Rode o programa via Maven:

```bash
mvn compile exec:java
```

Ou diretamente pela sua IDE, executando a classe `Main`.

Quando solicitado, digite o repositório no formato `usuario/repositorio`:

```
Digite o repositório (formato usuario/repositorio): facebook/react
```

O programa exibe o relatório no console e gera um arquivo `dados.json` na raiz do projeto, além de registrar essa execução num arquivo `historico.db` (SQLite), criado automaticamente na primeira vez que o programa roda.

## Visualizando o dashboard

1. Rode o programa normalmente para gerar o `dados.json`.
2. Sirva a pasta do projeto com um servidor local, por exemplo:
   ```bash
   python -m http.server 8000
   ```
3. Acesse `http://localhost:8000/dashboard.html` no navegador.

Alternativamente, abra o `dashboard.html` diretamente (duplo clique) e use o botão **"Selecionar dados.json"** para carregar o arquivo manualmente, sem precisar de servidor.

> O painel "Evolução do projeto" só aparece depois de rodar o programa pelo menos duas vezes para o mesmo repositório, já que ele compara execuções ao longo do tempo.

## Limitações conhecidas

- A contagem de issues usa paginação da API REST do GitHub, que tem um limite de 10.000 itens por endpoint. Para repositórios extremamente grandes (dezenas de milhares de issues), o número exibido pode ficar abaixo do real — o programa avisa quando isso acontece.
- A atividade semanal cobre apenas as últimas 52 semanas, conforme disponibilizado pela API do GitHub.
- A lista de colaboradores mostra até os 30 principais contribuidores retornados pela API numa única página, já ordenados por número de commits.
- Repositórios muito novos ou com pouca atividade podem não ter suas estatísticas de commits/atividade semanal calculadas ainda pelo GitHub; nesse caso o programa avisa e continua normalmente, com esses campos zerados até a próxima execução.

## Possíveis melhorias futuras

- Suporte a autenticação via GitHub App para limites de taxa mais altos.
- Paginação por cursor para contornar o limite de 10.000 itens em repositórios extremamente grandes.

## Autor

Eliezer Evangelista Silva
