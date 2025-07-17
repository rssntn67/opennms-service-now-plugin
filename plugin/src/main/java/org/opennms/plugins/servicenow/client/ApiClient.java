package org.opennms.plugins.servicenow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class ApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String TOKEN_END_POINT = "token";
    private static final String ALERT_END_POINT = "minnovo/a2a/servicenow/1.0/crea_aggiorna_allarmi";
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiClientCredentials apiClientCredentials;
    private TokenResponse tokenResponse;

    private static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };
    private static final SSLContext trustAllSslContext;
    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
    private static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();

    /*
     * This should not be used in production unless you really don't care
     * about the security. Use at your own risk.
     */
    public static OkHttpClient trustAllSslClient(OkHttpClient client) {
        OkHttpClient.Builder builder = client.newBuilder();
        builder.sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier((hostname, session) -> true);
        return builder.build();
    }

    public ApiClient(ApiClientCredentials apiClientCredentials) {
        this.apiClientCredentials = Objects.requireNonNull(apiClientCredentials);
        OkHttpClient okHttpclient = new OkHttpClient();

        if (apiClientCredentials.ignoreSslCertificateValidation) {
            okHttpclient = trustAllSslClient(okHttpclient);
        }
        this.client = okHttpclient;

    }

    public ApiClient(ApiClientCredentials apiClientCredentials, TokenResponse tokenResponse) {
        this.apiClientCredentials = Objects.requireNonNull(apiClientCredentials);
        OkHttpClient okHttpclient = new OkHttpClient();

        if (apiClientCredentials.ignoreSslCertificateValidation) {
            okHttpclient = trustAllSslClient(okHttpclient);
        }
        this.client = okHttpclient;
        this.tokenResponse = tokenResponse;
    }

    public void getAccessToken() throws IOException {
        FormBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", apiClientCredentials.username)
                .add("client_secret", apiClientCredentials.password)
                .build();
        Request request = new Request.Builder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .url(apiClientCredentials.url+"/"+ ApiClient.TOKEN_END_POINT)
                .post(formBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            // Parse the response to get the access token
            assert response.body() != null;
            this.tokenResponse=mapper.readValue(response.body().string(), TokenResponse.class);
            LOG.info("Access Token: {}", tokenResponse.getAccessToken());
        }
    }

    public CompletableFuture<Void> sendAlert(Alert alert) {
        return doPost(alert);
    }


    private CompletableFuture<Void> doPost(Object requestBodyPayload) {
        RequestBody body;
        try {
            body = RequestBody.create(mapper.writeValueAsString(requestBodyPayload),JSON);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Request request = new Request.Builder()
                .url(apiClientCredentials.url+"/"+ ApiClient.ALERT_END_POINT)
                .header("Authorization", "Bearer " + tokenResponse.getAccessToken())
                .post(body)
                .build();

        CompletableFuture<Void> future = new CompletableFuture<>();

        client.newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(@NotNull Call call, @NotNull IOException e) {
                        future.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) {
                        try (response) {
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
                        }
                    }
                });
        return future;
    }

    public String getToken() {
        return tokenResponse.getAccessToken();
    }

}
