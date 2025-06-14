package org.opennms.plugins.servicenow;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Route;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.ApiClientCredentials;

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

    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiClientCredentials apiClientCredentials;
    private final static String alertEndPoint = "crea_aggiorna_allarmi";

    public ApiClient(ApiClientCredentials apiClientCredentials) {
        this.apiClientCredentials = Objects.requireNonNull(apiClientCredentials);
        this.client = new OkHttpClient.Builder()
                .authenticator(new Authenticator() {
                    @Override
                    public Request authenticate(Route route, Response response) throws IOException {
                        String credential = Credentials.basic(apiClientCredentials.username, apiClientCredentials.password);
                        return response.request().newBuilder()
                                .header("Authorization", credential)
                                .build();
                    }
                })
                .build();
    }

    public CompletableFuture<Void> sendAlert(Alert alert) {
        return doPost(ApiClient.alertEndPoint, alert);
    }


    private CompletableFuture<Void> doPost(String url, Object requestBodyPayload) {
        RequestBody body;
        try {
            body = RequestBody.create(JSON, mapper.writeValueAsString(requestBodyPayload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String credentials = Credentials.basic(apiClientCredentials.username,apiClientCredentials.password);
        Request request = new Request.Builder()
                .url(apiClientCredentials.url+"/"+url)
                .header("Authorization", credentials)                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", ApiClient.class.getCanonicalName())
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
