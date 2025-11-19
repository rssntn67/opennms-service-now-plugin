package org.opennms.plugins.servicenow.shell;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.plugins.servicenow.EdgeService;

@Command(scope = "opennms-service-now", name = "get-parent-node", description = "Get Parent Node Data.")
@Service
public class EdgeServiceRunCommand implements Action {

    @Reference
    private EdgeService service;

    @Override
    public Object execute() {
        service.run();
        System.out.println(service.getParentMap());
        return null;
    }

}
