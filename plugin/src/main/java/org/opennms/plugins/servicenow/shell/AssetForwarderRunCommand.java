package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.api.console.Session;
import org.opennms.plugins.servicenow.AssetForwarder;

@Command(scope = "opennms-service-now", name = "asset-forwarder-run", description = "run asset forwarder.")
@Service
public class AssetForwarderRunCommand implements Action {

    @Reference
    private Session session;

    @Reference
    private AssetForwarder forwarder;

    @Override
    public Object execute() {
        forwarder.run();
        return null;
    }

}
