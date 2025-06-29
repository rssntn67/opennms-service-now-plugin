package org.opennms.plugins.servicenow.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);

    public ApiClientProviderImpl() {
    }


    ApiClient apiClient;
    ApiClientCredentials apiClientCredentials;
    @Override
    public ApiClient client(ApiClientCredentials credentials) throws ApiException {
        if (apiClientCredentials == null) {
            this.apiClientCredentials = credentials;
            apiClient = new ApiClient(credentials);
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
    public boolean validate(ApiClientCredentials credentials) {
        return true;
    }
}
