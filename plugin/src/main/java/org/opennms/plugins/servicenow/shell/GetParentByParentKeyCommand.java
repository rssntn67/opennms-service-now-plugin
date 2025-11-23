package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.EdgeService;

@Command(scope = "opennms-service-now", name = "get-parent-by-parent-key", description = "Get Parent Node Data Using parent key.")
@Service
public class GetParentByParentKeyCommand implements Action {

    @Reference
    private EdgeService service;

    @Argument(name = "nodeId", description = "node id of the asset", required = true)
    public int nodeId = -1;

    @Override
    public Object execute() {
        System.out.println(service.getParentByParentKey(nodeId));
        return null;
    }

}
