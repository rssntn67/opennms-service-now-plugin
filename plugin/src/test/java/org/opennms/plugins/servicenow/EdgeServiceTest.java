package org.opennms.plugins.servicenow;

import org.junit.Assert;
import org.junit.Test;
import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.NodeDao;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

public class EdgeServiceTest {


    private static Map<String, String> getGatewayMap() {
        Map<String, String> gatewayMap = new HashMap<>();
        gatewayMap.put("h1","gw");
        gatewayMap.put("h2","gw");
        gatewayMap.put("h3","gw");
        gatewayMap.put("h12","gw");
        gatewayMap.put("h13","gw");
        gatewayMap.put("h131","gw");
        gatewayMap.put("h132","gw");
        gatewayMap.put("h22","gw");
        gatewayMap.put("h33","gw");
        return gatewayMap;
    }

    private static Map<String, Set<String>> getEdgeMap() {
        Map<String, Set<String>> edgeMap = new HashMap<>();
        edgeMap.put("gw", new HashSet<>(List.of("h1", "h2", "h3")));
        edgeMap.put("h1", new HashSet<>(List.of("gw", "h12", "h13")));
        edgeMap.put("h2", new HashSet<>(List.of("gw", "h22")));
        edgeMap.put("h3", new HashSet<>(List.of("gw", "h33")));
        edgeMap.put("h12", new HashSet<>(List.of("h1")));
        edgeMap.put("h13", new HashSet<>(List.of("h1","h131","h132")));
        edgeMap.put("h22", new HashSet<>(List.of("h2")));
        edgeMap.put("h33", new HashSet<>(List.of("h3")));
        edgeMap.put("h131", new HashSet<>(List.of("h13")));
        edgeMap.put("h132", new HashSet<>(List.of("h13")));

        return edgeMap;
    }

    /**
     * Verifies that the object is serialized to JSON as expected.
     */
    @Test
    public void findParent() {
        NodeDao nodeDao = mock(NodeDao.class);
        EdgeDao edgeDao = mock(EdgeDao.class);
        EdgeService service = new EdgeService(
                edgeDao,
                nodeDao,
                "360000000",
                "3600000000",
                "5",
                "provision",
                "parent",
                "gateway",
                "SAND");

        service.destroy();
        service.runParentDiscovery(getEdgeMap(), getGatewayMap());

        Map<String, String> parentMap = service.getParentMap();

        System.out.println(parentMap);
        Assert.assertEquals(9, parentMap.size());
        Assert.assertEquals("gw", parentMap.get("h1"));
        Assert.assertEquals("gw", parentMap.get("h2"));
        Assert.assertEquals("gw", parentMap.get("h3"));
        Assert.assertEquals("h1", parentMap.get("h12"));
        Assert.assertEquals("h1", parentMap.get("h13"));
        Assert.assertEquals("h2", parentMap.get("h22"));
        Assert.assertEquals("h3", parentMap.get("h33"));
        Assert.assertEquals("h13", parentMap.get("h131"));
        Assert.assertEquals("h13", parentMap.get("h132"));

    }


}
