package org.opennms.plugins.servicenow.shell.connection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.connection.ConnectionManager;

@Command(scope = "opennms-service-now", name = "connection-delete", description = "Delete a connection", detailedDescription = "Deletes a connection for Cisco UCS Manager XML API")
@Service
public class DeleteConnectionCommand implements Action {

    @Reference
    private ConnectionManager connectionManager;


    @Override
    public Object execute() {
        if (this.connectionManager.deleteConnection()) {
            System.out.println("Connection deleted");
        } else {
            System.out.println("Connection not found");
        }
        return null;
    }
}
