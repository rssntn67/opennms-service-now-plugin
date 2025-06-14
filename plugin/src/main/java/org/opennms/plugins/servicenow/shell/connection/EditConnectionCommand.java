package org.opennms.plugins.servicenow.shell.connection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;

@Command(scope = "opennms-service-now", name = "connection-edit", description = "Edit a connection", detailedDescription = "Edit an existing connection for Cisco UCS Manger XML API")
@Service
public class EditConnectionCommand implements Action {

    @Reference
    private ConnectionManager connectionManager;

    @Reference
    private ClientManager clientManager;

    @Option(name="-f", aliases="--force", description="Skip validation and save the connection as-is")
    public boolean skipValidation = false;

    @Option(name = "-i", aliases = "--ignore-ssl-certificate-validation", description = "Ignore ssl certificate validation")
    boolean ignoreSslCertificateValidation = false;

    @Argument(name = "url", description = "Cisco Ucs Manager XML API Url", required = true)
    public String url = null;

    @Argument(index = 1, name = "username", description = "Cisco Ucs Manager XML API username", required = true)
    public String username = null;

    @Argument(index = 2, name = "password", description = "Cisco Ucs Manager XML API password", required = true, censor = true)
    public String password = null;


    @Override
    public Object execute() {
        final var connection = this.connectionManager.getConnection();

        if (connection.isEmpty()) {
            System.err.println("No connection exists!");
            return null;
        }


        connection.get().setUrl(url);
        connection.get().setUsername(username);
        connection.get().setPassword(password);
        connection.get().setIgnoreSslCertificateValidation(ignoreSslCertificateValidation);
        System.err.println("updating: " + connection);


        if (!this.skipValidation) {
            final var error = clientManager.validate(connection.get());
            if (error.isPresent()) {
                System.err.println("Failed to validate credentials: " + error.get().message);
                return null;
            }
        }

        connection.get().save();
        System.out.println("Connection updated");

        return null;
    }
}
