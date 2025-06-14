package org.opennms.plugins.servicenow.shell.connection;

import it.xeniaprogetti.cisco.ucs.plugin.connection.ConnectionManager;
import it.xeniaprogetti.cisco.ucs.plugin.shell.AliasCompleter;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;

@Command(scope = "opennms-cucs", name = "connection-delete", description = "Delete a connection", detailedDescription = "Deletes a connection for Cisco UCS Manager XML API")
@Service
public class DeleteConnectionCommand implements Action {

    @Reference
    private ConnectionManager connectionManager;

    @Argument(name = "alias", description = "Connection alias to delete", required = true)
    @Completion(AliasCompleter.class)
    public String alias = null;

    @Override
    public Object execute() {
        if (this.connectionManager.deleteConnection(alias)) {
            System.out.println("Connection deleted");
        } else {
            System.out.println("Connection not found");
        }
        return null;
    }
}
