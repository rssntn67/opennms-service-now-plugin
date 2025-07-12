package org.opennms.plugins.servicenow.client;

public interface ApiClientProvider {
    /**
     * Create a client WSO2 Api .
     *
     * @param credentials the credentials to use for the client.
     * @return a ApiClient client
     */
    ApiClient client(final ApiClientCredentials credentials) throws ApiException;

    boolean validate();

}
