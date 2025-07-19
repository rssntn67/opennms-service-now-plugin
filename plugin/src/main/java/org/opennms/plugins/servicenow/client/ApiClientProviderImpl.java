package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);

    public ApiClientProviderImpl() {
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

    @Override
    public void send(Alert alert, ApiClientCredentials credentials) throws ApiException {
        LOG.debug("send: {}, {}", credentials,alert);
        if (this.credentials != null && this.credentials.equals(credentials) ) {
            LOG.info("client: found: {}", credentials);
            checkExpiresAt();
        } else {
            LOG.info("client: found new: {}", credentials);
            this.credentials = credentials;
            this.tokenResponse = apiClient.getAccessToken(credentials);
            this.expiresAt = System.currentTimeMillis() + (tokenResponse.getExpires_in() * 1000L);
        }
        this.apiClient.sendAlert(alert, credentials, tokenResponse.getAccessToken());
    }

    @Override
    public void validate(ApiClientCredentials credentials) throws ApiException {
        apiClient.getAccessToken(credentials);
        LOG.info("validate: validated: {}", credentials);
    }

    private void checkExpiresAt() {
        long now = System.currentTimeMillis();
        if ( now >= expiresAt - 5000) { // 5 second buffer
            try {
                this.tokenResponse = apiClient.getAccessToken(credentials);
                this.expiresAt = System.currentTimeMillis() + (tokenResponse.getExpires_in() * 1000L);
            } catch (ApiException e) {
                LOG.error("check: access: code: {}, message: {}", e.getCode(), e.getResponseBody(),e);
            }
        }

    }
}
