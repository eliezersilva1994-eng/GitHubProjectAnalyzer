import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws Exception {

        // Reads the GitHub token from an environment variable / Lê o token do GitHub a partir de uma variável de ambiente
        String token = System.getenv("GITHUB_TOKEN");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite o repositório (formato usuario/repositorio): ");
        String repositorio = scanner.nextLine();

        // HTTP client shared by all requests / Cliente HTTP compartilhado por todas as requisições
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Fetches general repository data / Busca dados gerais do repositório
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());

        System.out.println("Nome: " + json.getString("full_name"));
        System.out.println("Estrelas: " + json.getInt("stargazers_count"));
        System.out.println("Linguagem principal: " + json.getString("language"));
        System.out.println("Issues abertas: " + json.getInt("open_issues_count"));

        // Fetches contributors / Busca contribuidores
        HttpRequest requestContribuidores = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/contributors"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
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

    // Counts issues by state using the Search API, which correctly excludes pull requests
    // Conta issues por estado usando a Search API, que exclui corretamente os pull requests
    private static int contarIssues(HttpClient client, String repositorio, String token, String estado) throws Exception {
        String query = "repo:" + repositorio + "+is:issue+state:" + estado;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/search/issues?q=" + query))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        return json.getInt("total_count");
    }

    // Fetches weekly commit activity; retries while GitHub is still computing the stats (HTTP 202)
    // Busca a atividade semanal de commits; tenta novamente enquanto o GitHub ainda está calculando as estatísticas (HTTP 202)
    private static JSONArray buscarAtividadeSemanal(HttpClient client, String repositorio, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/" + repositorio + "/stats/commit_activity"))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
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