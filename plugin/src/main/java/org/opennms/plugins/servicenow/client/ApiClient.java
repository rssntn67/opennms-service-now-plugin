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

    public static final String TOKEN_END_POINT = "token";
    public static final String ALERT_END_POINT = "minnovo/a2a/servicenow/1.0/crea_aggiorna_allarmi";
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ApiClientCredentials apiClientCredentials;
    private TokenResponse tokenResponse;
    private long expiresAt;

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

    public ApiClient(ApiClientCredentials apiClientCredentials) throws ApiException {
        this.apiClientCredentials = Objects.requireNonNull(apiClientCredentials);
        OkHttpClient okHttpclient = new OkHttpClient();

        if (apiClientCredentials.ignoreSslCertificateValidation) {
            okHttpclient = trustAllSslClient(okHttpclient);
        }
        this.client = okHttpclient;
        getAccessToken();
    }

    private void refreshToken() throws ApiException {
        FormBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", tokenResponse.getRefreshToken())
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
                if (response.body() != null) {
                    LOG.warn("refreshToken: code: {}, response: {}", response.code(), response.body().string());
                    throw new ApiException("Unexpected code: ", new RuntimeException(), response.code(), response.body().toString());
                }
                LOG.warn("refreshToken: code {} response: null", response.code());
                throw new ApiException("Unexpected code: ", new RuntimeException(), response.code(), "");
            }
            // Parse the response to get the access token
            assert response.body() != null;
            this.tokenResponse=mapper.readValue(response.body().string(), TokenResponse.class);
            this.expiresAt = System.currentTimeMillis() + (this.tokenResponse.getExpires_in() * 1000L);
            LOG.info("refreshToken: Access Token: {}", tokenResponse.getAccessToken());
        } catch (IOException e) {
            throw new ApiException("Got IOException",e);
        }
    }


    private void getAccessToken() throws ApiException {
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
                if (response.body() != null) {
                    LOG.warn("getAccessToken: code: {}, response: {}", response.code(), response.body().string());
                    throw new ApiException("Unexpected code: ", new RuntimeException(), response.code(), response.body().toString());
                }
                LOG.warn("getAccessToken: code {} response: null", response.code());
                throw new ApiException("Unexpected code: ", new RuntimeException(), response.code(), "");
            }
            // Parse the response to get the access token
            assert response.body() != null;
            this.tokenResponse=mapper.readValue(response.body().string(), TokenResponse.class);
            this.expiresAt = System.currentTimeMillis() + (this.tokenResponse.getExpires_in() * 1000L);
            LOG.info("getAccessToken: Access Token: {}", tokenResponse.getAccessToken());
        } catch (IOException e) {
            throw new ApiException("Got IOException",e);
        }
    }

    public CompletableFuture<Void> sendAlert(Alert alert) {
        return doPost(alert);
    }

    public void check() {
        long now = System.currentTimeMillis();
        if (now < expiresAt && now >= expiresAt - 5000) { // 5 second buffer
            try {
                refreshToken();
            } catch (ApiException e) {
                LOG.error("check: refresh: code: {}, message: {}", e.getCode(), e.getResponseBody(),e);
            }
        } else if ( System.currentTimeMillis() >= expiresAt) {
            try {
                getAccessToken();
            } catch (ApiException e) {
                LOG.error("check: access: code: {}, message: {}", e.getCode(), e.getResponseBody(),e);
            }
        }
    }

    private CompletableFuture<Void> doPost(Object requestBodyPayload) {
        check();
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

    public TokenResponse getToken() {
        return tokenResponse;
    }

}
