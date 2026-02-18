package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.NetworkDevice;
import org.opennms.plugins.servicenow.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);
    private final String tokenEndPoint;
    private final String alertEndPoint;
    private final String assetEndPoint;

    public ApiClientProviderImpl(String tokenEndPoint, String alertEndPoint, String assetEndPoint) {
        this.tokenEndPoint = tokenEndPoint;
        this.alertEndPoint = alertEndPoint;
        this.assetEndPoint = assetEndPoint;
    }

    private final ApiClient apiClient = new ApiClient();
    private ApiClientCredentials credentials;
    private long expiresAt = System.currentTimeMillis();

    public TokenResponse getTokenResponse() {
        return tokenResponse;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public ApiClientCredentials getCredentials() {
        return credentials;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    private TokenResponse tokenResponse;

    private void getTokenResponse(ApiClientCredentials credentials) throws ApiException {
        if (this.credentials != null && this.credentials.equals(credentials) ) {
            LOG.debug("client: found: {}", credentials);
            checkExpiresAt();
        } else {
            LOG.debug("client: found new: {}", credentials);
            this.credentials = credentials;
            this.tokenResponse = apiClient.getAccessToken(credentials, tokenEndPoint);
            this.expiresAt = System.currentTimeMillis() + (tokenResponse.getExpires_in() * 1000L);
        }
    }

    private void checkExpiresAt() {
        long now = System.currentTimeMillis();
        if ( now >= expiresAt - 5000) { // 5 second buffer
            try {
                this.tokenResponse = apiClient.getAccessToken(credentials, tokenEndPoint);
                this.expiresAt = System.currentTimeMillis() + (tokenResponse.getExpires_in() * 1000L);
            } catch (ApiException e) {
                LOG.error("check: access: code: {}, message: {}", e.getCode(), e.getResponseBody(),e);
            }
        }
    }


    @Override
    public void send(Alert alert, ApiClientCredentials credentials) throws ApiException {
        getTokenResponse(credentials);
        this.apiClient.sendAlert(alert, credentials, tokenResponse.getAccessToken(), alertEndPoint);
    }

    @Override
    public void send(NetworkDevice networkDevice, ApiClientCredentials credentials) throws ApiException {
        getTokenResponse(credentials);
        this.apiClient.sendAsset(networkDevice, credentials, tokenResponse.getAccessToken(), assetEndPoint);
    }

    @Override
    public void send(AccessPoint accessPoint, ApiClientCredentials credentials) throws ApiException {
        getTokenResponse(credentials);
        this.apiClient.sendAsset(accessPoint, credentials, tokenResponse.getAccessToken(), assetEndPoint);
    }

    @Override
    public void validate(ApiClientCredentials credentials) throws ApiException {
        apiClient.getAccessToken(credentials, tokenEndPoint);
        LOG.info("validate: validated: {}", credentials);
    }

}
