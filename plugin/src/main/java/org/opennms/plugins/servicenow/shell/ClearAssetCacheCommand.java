package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.AssetForwarder;

@Command(scope = "opennms-service-now", name = "clear-asset-cache", description = "Clear the asset cache.")
@Service
public class ClearAssetCacheCommand implements Action {

    @Reference
    private AssetForwarder forwarder;

    @Override
    public Object execute() {
        forwarder.clearCache();
        System.out.println("Asset cache cleared.");
        return null;
    }
}