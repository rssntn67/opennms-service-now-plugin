package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);

    public ApiClientProviderImpl() {
    }

    ApiClient apiClient;
    ApiClientCredentials credentials;
    private long expiresAt = System.currentTimeMillis();

    @Override
    public ApiClient client(ApiClientCredentials credentials) throws ApiException {
        LOG.debug("client: {}", credentials);
        if (this.credentials != null && this.credentials.equals(credentials) && this.apiClient != null) {
            LOG.info("client: found existing {}, for: {}",apiClient, credentials);
            checkExpiresAt();
            return this.apiClient;
        }
        LOG.info("client: building new api client from: {}", credentials);
        this.apiClient = new ApiClient(credentials);
        this.credentials = credentials;
        return this.apiClient;
    }

    private void checkExpiresAt() {
        long now = System.currentTimeMillis();
        if ( now >= expiresAt - 5000) { // 5 second buffer
            try {
                TokenResponse tokenResponse = apiClient.getAccessToken();
                this.expiresAt = System.currentTimeMillis() + (tokenResponse.getExpires_in() * 1000L);
            } catch (ApiException e) {
                LOG.error("check: access: code: {}, message: {}", e.getCode(), e.getResponseBody(),e);
            }
        }

    }
}
