package org.opennms.plugins.servicenow.shell.connection;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;

@Command(scope = "opennms-service-now", name = "connection-add", description = "Add a connection", detailedDescription = "Add a connection of Cisco UCS Manager XML API")
@Service
public class AddConnectionCommand implements Action {

    @Reference
    private ConnectionManager connectionManager;

    @Reference
    private ClientManager clientManager;

    @Option(name = "-t", aliases = "--test", description = "Dry run mode, test the credentials but do not save them")
    boolean dryRun = false;

    @Option(name="-f", aliases="--force", description="Skip validation and save the connection as-is")
    public boolean skipValidation = false;

    @Option(name = "-i", aliases = "--ignore-ssl-certificate-validation", description = "Ignore ssl certificate validation")
    boolean ignoreSslCertificateValidation = false;

    @Argument(name = "url", description = "Service Now API Url", required = true)
    public String url = null;

    @Argument(index = 1, name = "username", description = "Service Now API username", required = true)
    public String username = null;

    @Argument(index = 2, name = "password", description = "Service Now API password", required = true, censor = true)
    public String password = null;

    @Override
    public Object execute() {
        if (this.connectionManager.getConnection().isPresent()) {
            System.err.println("Connection already exists!");
            return null;
        }

        final var connection =
                this.connectionManager.newConnection(
                                this.url,
                                this.username,
                                this.password,
                        this.ignoreSslCertificateValidation
        );
        System.err.println("saving: " + connection);

        if (!this.skipValidation) {
            final var error = clientManager.validate(connection);
            if (error.isPresent()) {
                System.err.println("Failed to validate credentials: " + error.get().message);
                return null;
            }
        }

        if (this.dryRun) {
            System.out.println("Connection valid");
            return null;
        }

        connection.save();
        System.out.println("Connection created");

        return null;
    }
}
