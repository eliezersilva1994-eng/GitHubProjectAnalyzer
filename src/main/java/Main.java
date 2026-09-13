import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Scanner;
import java.time.Duration;

public class Main {

    public static void main(String[] args) throws Exception {

        // Reads the GitHub token from an environment variable / Lê o token do GitHub a partir de uma variável de ambiente
        String token = System.getenv("GITHUB_TOKEN");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite o repositório (formato usuario/repositorio): ");
        String repositorio = scanner.nextLine();

        // HTTP client shared by all requests, with a connect timeout so it never hangs forever
        // Cliente HTTP compartilhado por todas as requisições, com timeout de conexão pra nunca travar
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Fetches general repository data / Busca dados gerais do repositório
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());

        System.out.println("Nome: " + json.getString("full_name"));
        System.out.println("Estrelas: " + json.getInt("stargazers_count"));
        System.out.println("Linguagem principal: " + json.optString("language", "não definida"));
        System.out.println("Issues abertas: " + json.getInt("open_issues_count"));

        // Fetches contributors / Busca contribuidores
        HttpRequest requestContribuidores = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/contributors"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> responseContribuidores = client.send(requestContribuidores, HttpResponse.BodyHandlers.ofString());
        JSONArray contribuidores = new JSONArray(responseContribuidores.body());

        System.out.println("Contribuidores: " + contribuidores.length());

        // Fetches languages (bytes per language) and calculates percentages
        // Busca linguagens (bytes por linguagem) e calcula os percentuais
        HttpRequest requestLinguagens = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/languages"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> responseLinguagens = client.send(requestLinguagens, HttpResponse.BodyHandlers.ofString());
        JSONObject linguagens = new JSONObject(responseLinguagens.body());

        long totalBytes = 0;
        for (String key : linguagens.keySet()) {
            totalBytes += linguagens.getLong(key);
        }

        System.out.println("Linguagens:");
        for (String key : linguagens.keySet()) {
            double percentual = (linguagens.getLong(key) * 100.0) / totalBytes;
            System.out.printf("%-10s %.1f%%%n", key, percentual);
        }

        // Counts total commits / Conta o total de commits
        int totalCommits = contarCommits(client, repositorio, token);
        System.out.println("Total de commits: " + totalCommits);

        // Counts open and closed issues / Conta issues abertas e fechadas
        int issuesAbertas = contarIssues(client, repositorio, token, "open");
        int issuesFechadas = contarIssues(client, repositorio, token, "closed");
        System.out.println("Issues abertas: " + issuesAbertas);
        System.out.println("Issues fechadas: " + issuesFechadas);

        // Fetches weekly commit activity (last 52 weeks) / Busca a atividade semanal de commits (últimas 52 semanas)
        JSONArray atividadeSemanal = buscarAtividadeSemanal(client, repositorio, token);

        // Saves this run as a snapshot in the local database, then reads back the full
        // history for this repository, so the dashboard can show a real evolution over time.
        //
        // Salva essa execução como um retrato (snapshot) no banco local, depois lê de volta
        // o histórico completo desse repositório, pra o dashboard mostrar uma evolução real.
        JSONArray historico;
        try (java.sql.Connection conexao = Database.conectar()) {
            Database.salvarAnalise(conexao, repositorio, totalCommits, contribuidores.length(), issuesAbertas, issuesFechadas);
            historico = Database.buscarHistorico(conexao, repositorio);
        }

        // Builds the final JSON output that will feed the dashboard
        // Monta o JSON final que vai alimentar o dashboard
        JSONObject saida = new JSONObject();
        saida.put("nome", json.getString("full_name"));
        saida.put("commits", totalCommits);
        saida.put("contribuidores", contribuidores.length());
        saida.put("issuesAbertas", issuesAbertas);
        saida.put("issuesFechadas", issuesFechadas);
        saida.put("linguagens", linguagens);
        saida.put("atividadeSemanal", atividadeSemanal);
        saida.put("historico", historico);

        // Writes the JSON file to disk / Escreve o arquivo JSON no disco
        java.nio.file.Files.writeString(java.nio.file.Path.of("dados.json"), saida.toString(2));
        System.out.println("Arquivo dados.json gerado.");
    }

    // Counts total commits by summing each contributor's total from the stats API.
    // This works even for huge repositories, unlike the "Link" header trick, which
    // fails when GitHub omits the "last" page (common in large repos).
    //
    // Conta o total de commits somando o total de cada contribuidor na stats API.
    // Isso funciona mesmo em repositórios enormes, diferente do truque do header
    // "Link", que falha quando o GitHub omite a página "last" (comum em repos grandes).
    private static int contarCommits(HttpClient client, String repositorio, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/stats/contributors"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // GitHub may return 202 while it computes the stats for the first time.
        // O GitHub pode retornar 202 enquanto calcula as estatísticas pela primeira vez.
        int tentativas = 0;
        while (response.statusCode() == 202 && tentativas < 5) {
            Thread.sleep(1500);
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            tentativas++;
        }

        JSONArray contribuidoresStats = new JSONArray(response.body());
        int totalCommits = 0;
        for (int i = 0; i < contribuidoresStats.length(); i++) {
            totalCommits += contribuidoresStats.getJSONObject(i).getInt("total");
        }
        return totalCommits;
    }

    // Counts issues by state using the regular issues endpoint (not the Search API, which has
    // been unreliable with newer GitHub query parsing). This endpoint returns both issues and
    // pull requests mixed together, so we skip any item that has a "pull_request" key.
    //
    // Conta issues por estado usando o endpoint normal de issues (não a Search API, que anda
    // instável com o novo parser de query do GitHub). Esse endpoint retorna issues e pull
    // requests misturados, então pulamos qualquer item que tenha a chave "pull_request".
    private static int contarIssues(HttpClient client, String repositorio, String token, String estado) throws Exception {
        int total = 0;
        int pagina = 1;
        int LIMITE_PAGINAS = 300; // safety cap: 300 x 100 = up to 30,000 items / limite de segurança

        while (pagina <= LIMITE_PAGINAS) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repositorio
                            + "/issues?state=" + estado + "&per_page=100&page=" + pagina))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // The GitHub REST API caps this kind of pagination at 10,000 items (page 100 x
                // per_page 100). For very large repositories the real count may be higher, so
                // we warn instead of silently returning a number that looks precise but isn't.
                //
                // A REST API do GitHub limita esse tipo de paginação a 10.000 itens (página
                // 100 x per_page 100). Para repositórios muito grandes o total real pode ser
                // maior, então avisamos em vez de devolver um número que parece exato mas não é.
                System.out.println("Aviso: a contagem de issues " + estado
                        + " parou em " + total + " (limite de paginação da API do GitHub atingido)."
                        + " Para repositórios muito grandes esse número pode estar abaixo do real.");
                break;
            }

            JSONArray itens = new JSONArray(response.body());

            if (itens.isEmpty()) {
                break;
            }

            for (int i = 0; i < itens.length(); i++) {
                if (!itens.getJSONObject(i).has("pull_request")) {
                    total++;
                }
            }

            System.out.println("  (" + estado + ") página " + pagina + " lida, total parcial: " + total);

            pagina++;
        }

        return total;
    }

    // Fetches weekly commit activity; retries while GitHub is still computing the stats (HTTP 202)
    // Busca a atividade semanal de commits; tenta novamente enquanto o GitHub ainda está calculando as estatísticas (HTTP 202)
    private static JSONArray buscarAtividadeSemanal(HttpClient client, String repositorio, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/stats/commit_activity"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int tentativas = 0;
        while (response.statusCode() == 202 && tentativas < 5) {
            Thread.sleep(1500); // GitHub is still processing / GitHub ainda está processando
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            tentativas++;
        }

        // Each item: {total, week, days: [...]} / Cada item: {total, week, days: [...]}
        return new JSONArray(response.body());
    }
}