package org.opennms.plugins.servicenow.client;

public interface ApiClientProvider {
    /**
     * Create a client for a Cisco UCS Manager XML Api .
     *
     * @param credentials the credentials to use for the client.
     * @return a NutanixApiClient client
     */
    ApiClient client(final ApiClientCredentials credentials) throws ApiException;

    boolean validate(final ApiClientCredentials credentials);

}
