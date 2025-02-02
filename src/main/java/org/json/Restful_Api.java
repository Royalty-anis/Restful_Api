import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.json.JSONArray;

public class Restful_Api {
    private static final String CLIENT_ID = "547a11d1bc804a1097cb4459a5a0b0c4";
    private static final String CLIENT_SECRET = "feaf3b71e00e489080f9e30768d3bb6b";
    private static final String REDIRECT_URI = "http://localhost:8080/callback";
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search?q=";
    private static final String API_URL = "https://api.spotify.com/v1/me/top/tracks";

    private static String accessToken = "";
    private static String refreshToken = "";

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Open this URL in your browser to authorize the app:");
        String authLink = AUTH_URL + "?client_id=" + CLIENT_ID + "&response_type=code" +
                "&redirect_uri=" + REDIRECT_URI + "&scope=user-top-read";
        System.out.println(authLink);

        // Start local server to listen for callback
        String authCode = startHttpServer();

        if (authCode != null) {
            getAccessToken(authCode);

            System.out.print("Enter an artist name (or press Enter to skip): ");
            String artistName = scanner.nextLine().trim();

            if (!artistName.isEmpty()) {
                fetchSongsByArtist(artistName);
            } else {
                fetchTopTracks();
            }
        }
    }

    private static String startHttpServer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final String[] authCode = new String[1];

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("code=")) {
                authCode[0] = query.split("code=")[1];

                String response = "Authorization successful! You can close this tab.";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                latch.countDown();
            }
        });

        System.out.println("Waiting for authentication...");
        server.start();
        latch.await();
        server.stop(1);
        return authCode[0];
    }

    private static void getAccessToken(String authCode) throws IOException, InterruptedException {
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
            refreshToken = jsonResponse.optString("refresh_token", "");
            System.out.println("Access Token: " + accessToken);
        } else {
            System.err.println("Failed to get access token: " + response.body());
        }
    }

    private static void fetchTopTracks() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            refreshAccessToken();
            fetchTopTracks();
            return;
        }

        if (response.statusCode() == 200) {
            parseAndPrintTracks(response.body());
        } else {
            System.err.println("Failed to fetch top tracks: " + response.body());
        }
    }

    private static void fetchSongsByArtist(String artistName) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String query = artistName.replace(" ", "%20");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SEARCH_URL + query + "&type=track&limit=10"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            refreshAccessToken();
            fetchSongsByArtist(artistName);
            return;
        }

        if (response.statusCode() == 200) {
            parseAndPrintTracks(response.body());
        } else {
            System.err.println("Failed to fetch songs for artist: " + response.body());
        }
    }

    private static void refreshAccessToken() throws IOException, InterruptedException {
        if (refreshToken.isEmpty()) {
            System.err.println("Refresh token not available. Please re-authenticate.");
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        String requestBody = "grant_type=refresh_token&refresh_token=" + refreshToken;

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
            System.out.println("Access Token Refreshed: " + accessToken);
        } else {
            System.err.println("Failed to refresh access token: " + response.body());
        }
    }

    private static void parseAndPrintTracks(String jsonResponse) {
        JSONObject jsonObject = new JSONObject(jsonResponse);
        JSONArray tracks = jsonObject.getJSONObject("tracks").getJSONArray("items");

        System.out.println("\n🎵 Songs Found:");
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.getJSONObject(i);
            String songName = track.getString("name");
            JSONArray artistsArray = track.getJSONArray("artists");

            StringBuilder artists = new StringBuilder();
            for (int j = 0; j < artistsArray.length(); j++) {
                if (j > 0) artists.append(", ");
                artists.append(artistsArray.getJSONObject(j).getString("name"));
            }

            System.out.println((i + 1) + ". " + songName + " - " + artists);
        }
    }
}
