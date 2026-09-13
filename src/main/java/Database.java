import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

// Handles saving and reading the history of analyses in a local SQLite database.
// Cuida de salvar e ler o histórico de análises num banco SQLite local.
public class Database {

    // The database is a single file, created automatically in the project folder.
    // O banco é um único arquivo, criado automaticamente na pasta do projeto.
    private static final String URL_BANCO = "jdbc:sqlite:historico.db";

    // Opens a connection and makes sure the table exists before returning it.
    // Abre uma conexão e garante que a tabela existe antes de devolvê-la.
    public static Connection conectar() throws Exception {
        Connection conexao = DriverManager.getConnection(URL_BANCO);
        criarTabelaSeNaoExistir(conexao);
        return conexao;
    }

    private static void criarTabelaSeNaoExistir(Connection conexao) throws Exception {
        String sql = """
                CREATE TABLE IF NOT EXISTS analises (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    repositorio TEXT NOT NULL,
                    data_hora TEXT NOT NULL,
                    commits INTEGER,
                    contribuidores INTEGER,
                    issues_abertas INTEGER,
                    issues_fechadas INTEGER
                )
                """;
        try (Statement stmt = conexao.createStatement()) {
            stmt.execute(sql);
        }
    }

    // Saves one snapshot of the current analysis / Salva um retrato (snapshot) da análise atual
    public static void salvarAnalise(Connection conexao, String repositorio, int commits,
                                     int contribuidores, int issuesAbertas, int issuesFechadas) throws Exception {
        String sql = """
                INSERT INTO analises (repositorio, data_hora, commits, contribuidores, issues_abertas, issues_fechadas)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, repositorio);
            stmt.setString(2, Instant.now().toString());
            stmt.setInt(3, commits);
            stmt.setInt(4, contribuidores);
            stmt.setInt(5, issuesAbertas);
            stmt.setInt(6, issuesFechadas);
            stmt.executeUpdate();
        }
    }

    // Returns every past analysis for this repository, oldest first, as a JSON array.
    // Devolve todas as análises passadas desse repositório, da mais antiga pra mais nova, como um array JSON.
    public static JSONArray buscarHistorico(Connection conexao, String repositorio) throws Exception {
        String sql = """
                SELECT data_hora, commits, contribuidores, issues_abertas, issues_fechadas
                FROM analises
                WHERE repositorio = ?
                ORDER BY data_hora ASC
                """;
        JSONArray historico = new JSONArray();

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, repositorio);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject registro = new JSONObject();
                    registro.put("dataHora", rs.getString("data_hora"));
                    registro.put("commits", rs.getInt("commits"));
                    registro.put("contribuidores", rs.getInt("contribuidores"));
                    registro.put("issuesAbertas", rs.getInt("issues_abertas"));
                    registro.put("issuesFechadas", rs.getInt("issues_fechadas"));
                    historico.put(registro);
                }
            }
        }

        return historico;
    }
}