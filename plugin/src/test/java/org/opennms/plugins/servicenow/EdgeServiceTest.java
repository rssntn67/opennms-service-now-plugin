package org.opennms.plugins.servicenow;

import org.junit.Assert;
import org.junit.Test;
import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.InterfaceToNodeCache;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.TopologyEdge;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.integration.api.v1.model.immutables.ImmutableIpInterface;
import org.opennms.integration.api.v1.model.immutables.ImmutableMetaData;
import org.opennms.integration.api.v1.model.immutables.ImmutableNode;
import org.opennms.integration.api.v1.model.immutables.ImmutableNodeCriteria;
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyEdge;
import org.opennms.integration.api.v1.model.immutables.ImmutableTopologyPort;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EdgeServiceTest {
    private final static int startId = 11;
    private final static int endId = 20;

    private static Set<TopologyEdge> getEdges() throws UnknownHostException {
       return getEdges(getSwitch(),getNodes().stream().filter(node -> !node.getLabel().equals("switch")).toList());
    }
    private static EdgeService getEdgeServiceMock() throws UnknownHostException {
        NodeDao nodeDao = mock(NodeDao.class);
        when(nodeDao.getNodes()).thenReturn(getNodes());
        when(nodeDao.getNodeByForeignSourceAndForeignId("EdgeServiceTest", "gateway")).thenReturn(getGateway());
        when(nodeDao.getNodeByForeignSourceAndForeignId("EdgeServiceTest", "switch")).thenReturn(getSwitch());
        for (int i = startId; i < endId; i++) {
            when(nodeDao.getNodeByForeignSourceAndForeignId("EdgeServiceTest", "node"+i)).thenReturn(getNode(i));
        }
        when(nodeDao.getNodeById(getGateway().getId())).thenReturn(getGateway());
        EdgeDao edgeDao = mock(EdgeDao.class);
        when(edgeDao.getEdges(TopologyProtocol.LLDP))
                .thenReturn(getEdges());
        when(edgeDao.getEdges(TopologyProtocol.BRIDGE))
                .thenReturn(new HashSet<>());

        InterfaceToNodeCache cache = mock(InterfaceToNodeCache.class);
        when(cache.getFirstNodeId("TEST", InetAddress.getByName("10.10.10.254")))
                .thenReturn(Optional.of(getGateway().getId()));

        EdgeService service = new EdgeService(
                edgeDao,
                nodeDao,
                cache,
                "1000000",
                "3600000",
                "10",
                "provision",
                "parent",
                "gateway",
                "NODES"
        );
        service.init();
        service.destroy();
        return service;
    }

    private static TopologyEdge getEdge(Node parent, Node child) {
        return ImmutableTopologyEdge.newBuilder()
                .setId("test"+parent.getId()+ child.getId())
                .setProtocol(TopologyProtocol.LLDP)
                .setSource(ImmutableTopologyPort.newBuilder()
                        .setId(parent.getForeignId())
                        .setTooltipText("")
                        .setIfIndex(1)
                        .setIfName("")
                        .setIfAddress("")
                        .setNodeCriteria(ImmutableNodeCriteria.newBuilder()
                                .setForeignId(parent.getForeignId())
                                .setForeignSource(parent.getForeignSource())
                                .build())

                        .build())
                .setTarget(ImmutableTopologyPort.newBuilder()
                        .setId(child.getForeignId())
                        .setTooltipText("")
                        .setIfIndex(1)
                        .setIfName("")
                        .setIfAddress("")
                        .setNodeCriteria(ImmutableNodeCriteria.newBuilder()
                                .setForeignId(child.getForeignId())
                                .setForeignSource(child.getForeignSource())
                                .build())
                        .build())
                .setTooltipText("test")
                .build();
    }

    private static Set<TopologyEdge> getEdges(Node parent, List<Node> children) {
        return children.stream()
                .map(child -> getEdge(parent, child))
                .collect(Collectors.toSet());
    }

    private static Map<String, String> getGatewayMap() {
        Map<String, String> gatewayMap = new HashMap<>();
        gatewayMap.put("h1","gw1");
        gatewayMap.put("h2","gw1");
        gatewayMap.put("h3","gw1");
        gatewayMap.put("h12","gw1");
        gatewayMap.put("h13","gw1");
        gatewayMap.put("h131","gw1");
        gatewayMap.put("h132","gw1");
        gatewayMap.put("h22","gw1");
        gatewayMap.put("h33","gw1");
        return gatewayMap;
    }

    private static Map<String, Set<String>> getEdgeMap() {
        Map<String, Set<String>> edgeMap = new HashMap<>();
        edgeMap.put("gw1", new HashSet<>(List.of("sw", "gw2")));
        edgeMap.put("gw2", new HashSet<>(List.of("sw", "gw1")));
        edgeMap.put("sw", new HashSet<>(List.of("gw1","gw2","h1", "h2", "h3")));
        edgeMap.put("h1", new HashSet<>(List.of("sw", "h12", "h13")));
        edgeMap.put("h2", new HashSet<>(List.of("sw", "h22")));
        edgeMap.put("h3", new HashSet<>(List.of("sw", "h33")));
        edgeMap.put("h12", new HashSet<>(List.of("h1")));
        edgeMap.put("h13", new HashSet<>(List.of("h1","h131","h132")));
        edgeMap.put("h22", new HashSet<>(List.of("h2")));
        edgeMap.put("h33", new HashSet<>(List.of("h3")));
        edgeMap.put("h131", new HashSet<>(List.of("h13")));
        edgeMap.put("h132", new HashSet<>(List.of("h13")));

        return edgeMap;
    }

    private static Node getSwitch() throws UnknownHostException{
        return ImmutableNode
                .newBuilder()
                .setLabel("switch")
                .setForeignId("switch")
                .setForeignSource("EdgeServiceTest")
                .setLocation("TEST")
                .setId(10)
                .addMetaData(ImmutableMetaData
                        .newBuilder()
                        .setContext("provision")
                        .setKey("gateway")
                        .setValue("10.10.10.254")
                        .build())
                .addIpInterface(ImmutableIpInterface
                        .newBuilder()
                        .setIpAddress(InetAddress.getByName("10.10.10.10"))
                        .build())
                .build();

    }

    private static Node getGateway() throws UnknownHostException{
        return ImmutableNode
                .newBuilder()
                .setLabel("gateway")
                .setForeignId("gateway")
                .setForeignSource("EdgeServiceTest")
                .setLocation("TEST")
                .setId(254)
                .addIpInterface(ImmutableIpInterface
                        .newBuilder()
                        .setIpAddress(InetAddress.getByName("10.10.10.254"))
                        .build())
                .build();
    }

    private static Node getNode(int i) throws UnknownHostException{
            return ImmutableNode
                    .newBuilder()
                    .setLabel("node" + i)
                    .setForeignId("node" + i)
                    .setForeignSource("EdgeServiceTest")
                    .setLocation("TEST")
                    .setId(i)
                    .addMetaData(ImmutableMetaData
                            .newBuilder()
                            .setContext("provision")
                            .setKey("gateway")
                            .setValue("10.10.10.254")
                            .build())
                    .addIpInterface(ImmutableIpInterface
                            .newBuilder()
                            .setIpAddress(InetAddress.getByName("10.10.10." + i))
                            .build())
                    .build();
    }

    private static List<Node> getNodes() throws UnknownHostException {
        List<Node> nodes = new ArrayList<>();
        for (int i = startId; i < endId; i++) {
            nodes.add(getNode(i));
        }
        nodes.add(getGateway());
        nodes.add(getSwitch());
        return nodes;

    }


    /**
     * Verifies that the object is serialized to JSON as expected.
     */
    @Test
    public void findParentTest() throws UnknownHostException {
        EdgeService edgeService = getEdgeServiceMock();
        Map<String, String> parentMap =
                edgeService.runParentDiscovery(getEdgeMap(), getGatewayMap());

        System.out.println(parentMap);
        Assert.assertEquals(9, parentMap.size());
        Assert.assertEquals("sw", parentMap.get("h1"));
        Assert.assertEquals("sw", parentMap.get("h2"));
        Assert.assertEquals("sw", parentMap.get("h3"));
        Assert.assertEquals("h1", parentMap.get("h12"));
        Assert.assertEquals("h1", parentMap.get("h13"));
        Assert.assertEquals("h2", parentMap.get("h22"));
        Assert.assertEquals("h3", parentMap.get("h33"));
        Assert.assertEquals("h13", parentMap.get("h131"));
        Assert.assertEquals("h13", parentMap.get("h132"));

    }

    @Test
    public void nodeGatewayMapTest() throws UnknownHostException {

        EdgeService edgeService = getEdgeServiceMock();
        List<Node> nodes = getNodes();
        Assert.assertEquals(11, nodes.size());
        Map<String, String> nodeGatewayMap = edgeService.runNodeLabelToGatewayMap(nodes);
        System.out.println(nodeGatewayMap);
        Assert.assertEquals(10, nodeGatewayMap.size());
        Assert.assertFalse(nodeGatewayMap.containsKey("10.10.10.254"));
        for (String value : new HashSet<>(nodeGatewayMap.values()))
            Assert.assertEquals("10.10.10.254", value);
    }

    @Test
    public void testGetEdgeMap() throws UnknownHostException {
        EdgeService edgeService = getEdgeServiceMock();
        Set<TopologyEdge> edges = getEdges();
        final Map<String, Set<String>> edgeMap = edgeService.runEdgeMap(edges);
        Assert.assertEquals(11, edgeMap.size());
        System.out.println(edgeMap);
        final Set<String> children = edgeMap.get(getSwitch().getLabel());
        Assert.assertEquals(10, children.size());

        final Map<String, Set<String>> map = edgeService.runEdgeMap(new HashSet<>());
        System.out.println(map);
        Assert.assertEquals(0, map.size());
    }

}

