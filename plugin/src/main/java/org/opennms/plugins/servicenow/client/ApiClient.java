package org.opennms.plugins.servicenow.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.NetworkDevice;
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

public class ApiClient {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient clientTrueSsl = new OkHttpClient();
    private final OkHttpClient clientIgnoreSsl = trustAllSslClient(clientTrueSsl);
    private final ObjectMapper mapper = new ObjectMapper();

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


    public TokenResponse getAccessToken(ApiClientCredentials credentials, String tokenEndPoint) throws ApiException {
        OkHttpClient client;
        if (credentials.ignoreSslCertificateValidation ) {
            client = trustAllSslClient(clientIgnoreSsl);
        } else {
            client = clientTrueSsl;
        }
        FormBody formBody = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", credentials.username)
                .add("client_secret", credentials.password)
                .build();
        Request request = new Request.Builder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .url(credentials.url+"/"+ tokenEndPoint)
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
            String json= response.body().string();
            return mapper.readValue(json, TokenResponse.class);
        } catch (IOException e) {
            throw new ApiException("Got IOException",e);
        }
    }

    public void sendAlert(Alert alert, ApiClientCredentials credentials, String tokenString, String endpoint) throws ApiException {
        doPost(alert, credentials, tokenString, endpoint);
    }

    public void sendAsset(AccessPoint ap, ApiClientCredentials credentials, String tokenString, String endpoint) throws ApiException {
        doPost(ap, credentials, tokenString, endpoint);
    }

    public void sendAsset(NetworkDevice nd, ApiClientCredentials credentials, String tokenString, String endpoint) throws ApiException {
        doPost(nd, credentials, tokenString, endpoint);
    }


    private void doPost(Object requestBodyPayload, ApiClientCredentials credentials, String tokenString, String endpoint) throws ApiException {
        OkHttpClient client;
        if (credentials.ignoreSslCertificateValidation ) {
            client = trustAllSslClient(clientIgnoreSsl);
        } else {
            client = clientTrueSsl;
        }
        RequestBody body;
        try {
            String jsonPayLoad = mapper.writeValueAsString(requestBodyPayload);
            LOG.debug("doPost: body: \\n {}", jsonPayLoad);
            body = RequestBody.create(jsonPayLoad, JSON);
        } catch (JsonProcessingException e) {
            throw new ApiException("Error processing JSON", e);
        }

        Request request = new Request.Builder()
                .url(credentials.url+"/"+ endpoint)
                .header("Authorization", "Bearer " + tokenString)
                .post(body)
                .build();

        LOG.debug("doPost: requesting url: {}", request.url());

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.body() != null) {
                    LOG.warn("doPost: code: {}, response: {}", response.code(), response.body().string());
                    throw new ApiException("doPost: Unexpected code: ", new RuntimeException(), response.code(), response.body().toString());
                }
                LOG.warn("doPost: code {} response: null", response.code());
                throw new ApiException("Unexpected code: ", new RuntimeException(), response.code(), "");
            }
        } catch (IOException e) {
            throw new ApiException("Got IOException",e);
        }

    }
}
