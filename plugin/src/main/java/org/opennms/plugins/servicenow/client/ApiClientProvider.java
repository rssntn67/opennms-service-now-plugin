package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.Alert;

public interface ApiClientProvider {
    /**
     * Send Alert to client WSO2 Api .
     *
     * @param alert the alert to send data.
     * @param credentials the credentials to use for the client.
     */
    void send(Alert alert, final ApiClientCredentials credentials) throws ApiException;

    void validate(ApiClientCredentials credentials) throws ApiException;
}
