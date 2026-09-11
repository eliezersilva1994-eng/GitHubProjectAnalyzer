import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.json.JSONObject;

public class Main {

    public static void main(String[] args) throws Exception {

        String token = System.getenv("GITHUB_TOKEN");
        String repositorio = "torvalds/linux"; // exemplo pra testar

        HttpClient client = HttpClient.newHttpClient();

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
    }
}