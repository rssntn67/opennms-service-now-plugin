package org.opennms.plugins.servicenow.shell.connection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;

@Command(scope = "opennms-service-now", name = "connection-validate", description = "Validate a connection", detailedDescription = "Validate an existing connection to a Cisco UCS Manager XML API")
@Service
public class ValidateConnectionCommand implements Action {

    @Reference
    private ConnectionManager connectionManager;

    @Reference
    private ClientManager clientManager;

    @Override
    public Object execute() {
        final var connection = this.connectionManager.getConnection();
        if (connection.isEmpty()) {
            System.err.println("No connection exists!");
            return null;
        }

        final var error = clientManager.validate(connection.get());
        if (error.isPresent()) {
            System.err.println("Connection invalid: " + error.get().message);
        } else {
            System.out.println("Connection is valid");
        }

        return null;
    }
}
