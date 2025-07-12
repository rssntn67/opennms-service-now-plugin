package org.opennms.plugins.servicenow.client;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.Route;
import org.opennms.plugins.servicenow.model.Alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String TOKEN_URL = "https://example.com/oauth2/token";
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiClientCredentials apiClientCredentials;
    private final static String alertEndPoint = "crea_aggiorna_allarmi";
    private String accessToken;
    public ApiClient(ApiClientCredentials apiClientCredentials) {
        this.apiClientCredentials = Objects.requireNonNull(apiClientCredentials);
        this.client = new OkHttpClient.Builder().build();
    }

    public void getAccessToken() throws IOException {
        FormBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("scope", "SCOPE")
                .build();
        String credential = Credentials.basic(apiClientCredentials.username, apiClientCredentials.password);
        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .header("Authorization", "Bearer " + accessToken)                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            // Parse the response to get the access token
            assert response.body() != null;
            String responseBody = response.body().string();
            // In a real application, you'd use a JSON parser
            // This is a simplified extraction
            String accessToken = responseBody.split("\"access_token\":\"")[1].split("\"")[0];
            System.out.println("Access Token: " + accessToken);
            this.accessToken=accessToken;
        }
    }
    public CompletableFuture<Void> sendAlert(Alert alert) {
        return doPost(alert);
    }


    private CompletableFuture<Void> doPost(Object requestBodyPayload) {
        RequestBody body;
        try {
            body = RequestBody.create(JSON, mapper.writeValueAsString(requestBodyPayload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Request request = new Request.Builder()
                .url(apiClientCredentials.url+"/"+ ApiClient.alertEndPoint)
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        CompletableFuture<Void> future = new CompletableFuture<>();

        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        future.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        try {
                            if (!response.isSuccessful()) {
                                String bodyPayload = "(empty)";
                                ResponseBody body = response.body();
                                if (body != null) {
                                    try {
                                        bodyPayload = body.string();
                                    } catch (IOException e) {
                                        // pass
                                    }
                                    body.close();
                                }

                                future.completeExceptionally(new Exception("Request failed with response code: "
                                        + response.code() + " and body: " + bodyPayload));
                            } else {
                                future.complete(null);
                            }
                        } finally {
                            response.close();
                        }
                    }
                });
        return future;
    }

}
