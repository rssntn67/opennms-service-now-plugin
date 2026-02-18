package org.opennms.plugins.servicenow.client;

import org.opennms.plugins.servicenow.model.AccessPoint;
import org.opennms.plugins.servicenow.model.Alert;
import org.opennms.plugins.servicenow.model.NetworkDevice;

public interface ApiClientProvider {
    /**
     * Send Alert to client WSO2 Api .
     *
     * @param alert the alert to send data.
     * @param credentials the credentials to use for the client.
     */
    void send(Alert alert, final ApiClientCredentials credentials) throws ApiException;

    /**
     * Send NetworkDevice Asset to client WSO2 Api .
     *
     * @param networkDevice the asset to send data.
     * @param credentials the credentials to use for the client.
     */
    void send(NetworkDevice networkDevice, final ApiClientCredentials credentials) throws ApiException;

    /**
     * Send AccessPoint Asset to client WSO2 Api .
     *
     * @param accessPoint the asset to send data.
     * @param credentials the credentials to use for the client.
     */
    void send(AccessPoint accessPoint, final ApiClientCredentials credentials) throws ApiException;

    /**
     *
     * Check can get Authentication token with given Credentials
     *
     * @param credentials the credentials to use for the client
     */
    void validate(ApiClientCredentials credentials) throws ApiException;
}
