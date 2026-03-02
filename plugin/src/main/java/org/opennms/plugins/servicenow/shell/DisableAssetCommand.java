package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.AssetForwarder;

@Command(scope = "opennms-service-now", name = "disable-asset", description = "Set an asset as DISATTIVO in ServiceNow.")
@Service
public class DisableAssetCommand implements Action {

    @Reference
    private AssetForwarder forwarder;

    @Argument(name = "foreignSource", description = "Foreign source of the asset", required = true)
    public String foreignSource;

    @Argument(index = 1, name = "foreignId", description = "Foreign id of the asset", required = true)
    public String foreignId;

    @Override
    public Object execute() {
        if (forwarder.disableAsset(foreignSource, foreignId)) {
            System.out.println("Asset " + foreignSource + "::" + foreignId + " set to DISATTIVO.");
        } else {
            System.out.println("Asset " + foreignSource + "::" + foreignId + " not found in cache.");
        }
        return null;
    }
}