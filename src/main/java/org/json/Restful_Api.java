import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.io.OutputStream;

public class Restful_Api extends Application {
    private static final String CLIENT_ID = "547a11d1bc804a1097cb4459a5a0b0c4";
    private static final String CLIENT_SECRET = "feaf3b71e00e489080f9e30768d3bb6b";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search?q=";
    private static final String USER_TOP_TRACKS_URL = "https://api.spotify.com/v1/me/top/tracks?limit=10";

    private static String accessToken = "";
    private ListView<String> trackList = new ListView<>();
    private Button loginButton = new Button("Login to Spotify");
    private TextField searchField = new TextField();
    private Button searchButton = new Button("Search Songs");
    private Button topTracksButton = new Button("My Top 10 Songs");
    private Label statusLabel = new Label("Status: Not Logged In");

    @Override
    public void start(Stage primaryStage) {
        VBox vbox = new VBox(10, loginButton, searchField, searchButton, topTracksButton, trackList, statusLabel);
        vbox.setPadding(new Insets(15));
        vbox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vbox, 400, 500);
        primaryStage.setTitle("Spotify API JavaFX");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Set button actions
        loginButton.setOnAction(e -> authenticateSpotify());
        searchButton.setOnAction(e -> {
            String artistName = searchField.getText();
            if (artistName.isEmpty()) {
                statusLabel.setText("Please enter an artist name.");
            } else {
                statusLabel.setText("Searching for: " + artistName);
                fetchSongsByArtist(artistName);
            }
        });

        topTracksButton.setOnAction(e -> fetchUserTopTracks());
    }

    private void authenticateSpotify() {
        new Thread(() -> {
            try {
                String authLink = AUTH_URL + "?client_id=" + CLIENT_ID + "&response_type=code" +
                        "&redirect_uri=" + REDIRECT_URI + "&scope=user-top-read";
                System.out.println("Open this URL in your browser: " + authLink);

                String authCode = startHttpServer();
                getAccessToken(authCode);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private String startHttpServer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] authCode = new String[1];

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("code=")) {
                authCode[0] = query.split("code=")[1];
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write("Authorization successful! You can close this tab.".getBytes());
                os.close();
                latch.countDown();
            }
        });

        server.start();
        latch.await();
        server.stop(1);
        return authCode[0];
    }

    private void getAccessToken(String authCode) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        String requestBody = "grant_type=authorization_code&code=" + authCode + "&redirect_uri=" + REDIRECT_URI;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JSONObject jsonResponse = new JSONObject(response.body());
            accessToken = jsonResponse.getString("access_token");
            Platform.runLater(() -> statusLabel.setText("Login successful!"));
        } else {
            Platform.runLater(() -> statusLabel.setText("Login failed!"));
        }
    }

    private void fetchSongsByArtist(String artistName) {
        new Thread(() -> {
            try {
                if (accessToken.isEmpty()) {
                    Platform.runLater(() -> statusLabel.setText("Please log in first."));
                    return;
                }

                HttpClient client = HttpClient.newHttpClient();
                String query = URLEncoder.encode(artistName, StandardCharsets.UTF_8);
                String fullUrl = "https://api.spotify.com/v1/search?q=" + query + "&type=track&limit=10";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(fullUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    updateTrackListFromSearch(response.body());
                } else {
                    Platform.runLater(() -> statusLabel.setText("Search failed: " + response.body()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error fetching songs."));
            }
        }).start();
    }

    private void updateTrackListFromSearch(String jsonResponse) {
        Platform.runLater(() -> {
            trackList.getItems().clear();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject tracksObj = jsonObject.optJSONObject("tracks");

            if (tracksObj != null) {
                JSONArray tracks = tracksObj.optJSONArray("items");

                if (tracks != null) {
                    for (int i = 0; i < tracks.length(); i++) {
                        JSONObject track = tracks.getJSONObject(i);
                        String songName = track.getString("name");
                        JSONArray artists = track.getJSONArray("artists");

                        StringBuilder artistNames = new StringBuilder();
                        for (int j = 0; j < artists.length(); j++) {
                            if (j > 0) artistNames.append(", ");
                            artistNames.append(artists.getJSONObject(j).getString("name"));
                        }

                        trackList.getItems().add(songName + " - " + artistNames);
                    }
                } else {
                    trackList.getItems().add("No songs found.");
                }
            } else {
                trackList.getItems().add("No results found.");
            }
        });
    }

    private void fetchUserTopTracks() {
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(USER_TOP_TRACKS_URL))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                updateTrackList(response.body());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void updateTrackList(String jsonResponse) {
        Platform.runLater(() -> {
            trackList.getItems().clear();
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray tracks = jsonObject.optJSONArray("items");

            if (tracks != null) {
                for (int i = 0; i < tracks.length(); i++) {
                    JSONObject track = tracks.getJSONObject(i);
                    String songName = track.getString("name");
                    JSONArray artists = track.getJSONArray("artists");

                    StringBuilder artistNames = new StringBuilder();
                    for (int j = 0; j < artists.length(); j++) {
                        if (j > 0) artistNames.append(", ");
                        artistNames.append(artists.getJSONObject(j).getString("name"));
                    }

                    trackList.getItems().add(songName + " - " + artistNames);
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
