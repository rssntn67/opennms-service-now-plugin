package org.opennms.plugins.servicenow.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiClientProviderImpl implements ApiClientProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ApiClientProviderImpl.class);

    public ApiClientProviderImpl() {
    }

    ApiClient apiClient;
    ApiClientCredentials credentials;
    @Override
    public ApiClient client(ApiClientCredentials credentials) throws ApiException {
        LOG.warn("client: {}", credentials);
        if (this.credentials == null || !this.credentials.equals(credentials) ) {
            LOG.warn("client: building new api client from: {}", credentials);
            this.credentials = credentials;
            this.apiClient = new ApiClient(credentials);
        }
        return apiClient;
    }

}
