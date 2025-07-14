package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);

    private TokenResponse tokenResponse;

    public ApiClientProviderImpl() {
    }

    ApiClient apiClient;
    ApiClientCredentials apiClientCredentials;
    @Override
    public ApiClient client(ApiClientCredentials credentials) throws ApiException {
        if (apiClientCredentials == null) {
            this.apiClientCredentials = credentials;
            if (tokenResponse == null) {
                apiClient = new ApiClient(credentials);
            } else {
                apiClient = new ApiClient(credentials,tokenResponse);
            }

            return apiClient;
        }
        if (credentials.equals(apiClientCredentials)) {
            return apiClient;
        }
        apiClientCredentials = credentials;
        apiClient =new ApiClient(credentials);
        return apiClient;
    }

    @Override
    public boolean validate() {
        try {
            apiClient.getAccessToken();
        } catch (IOException e) {
            LOG.warn("validate: fails for {}", apiClientCredentials, e);
            return false;
        }
        return true;
    }

    @Override
    public void setToken(TokenResponse tokenResponse) {
        this.tokenResponse = tokenResponse;
    }
}
