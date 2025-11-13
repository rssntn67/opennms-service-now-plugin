package org.opennms.plugins.servicenow;

import org.junit.Assert;
import org.junit.Test;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EdgeServiceTest {

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

    private static Node getSwitch() throws UnknownHostException{
        return ImmutableNode
                .newBuilder()
                .setLabel("switch")
                .setForeignId("switch")
                .setForeignSource("EdgeServiceTest")
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
        for (int i = 11; i < 20; i++) {
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
    public void findParentTest() {
        Map<String, String> parentMap =
                EdgeService.runParentDiscovery(getEdgeMap(), getGatewayMap(), 5);

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

    @Test
    public void testGetNodeGatewayMapTest() throws UnknownHostException {

        List<Node> nodes = getNodes();
        Assert.assertEquals(11, nodes.size());
        Map<String, String> nodeGatewayMap = EdgeService.getNodeGatewayMap(nodes, "provision", "gateway", "EXCLUDED");
        System.out.println(nodeGatewayMap);
        Assert.assertEquals(10, nodeGatewayMap.size());
        Assert.assertFalse(nodeGatewayMap.containsKey("gateway"));
        for (String value : new HashSet<>(nodeGatewayMap.values()))
            Assert.assertEquals("gateway", value);
    }

    @Test
    public void testGetEdgeMap() throws UnknownHostException {
        final List<Node> nodes = getNodes();
        Assert.assertEquals(11, nodes.size());
        Node sw = getSwitch();
        final Set<TopologyEdge> edges = getEdges(getSwitch(), nodes.stream().filter(node -> !node.getLabel().equals(sw.getLabel())).toList());
        Assert.assertEquals(11, nodes.size());
        Assert.assertEquals(10, edges.size());
        final Map<String, Set<String>> edgeMap = EdgeService.getEdgeMap(nodes,edges);
        Assert.assertEquals(11, edgeMap.size());
        System.out.println(edgeMap);
        final Set<String> children = edgeMap.get(sw.getLabel());
        Assert.assertEquals(10, children.size());
        nodes.stream()
                .filter(node -> !node.getLabel().equals(sw.getLabel()))
                .forEach( node -> {
                    Set<String> connected = edgeMap.get(node.getLabel());
                    Assert.assertEquals(1, connected.size());
                    Assert.assertTrue(connected.contains(sw.getLabel()));
                    Assert.assertTrue(children.contains(node.getLabel()));
                });

        
        final Map<String, Set<String>> map = EdgeService.getEdgeMap(nodes, Collections.emptySet());
        System.out.println(map);
        Assert.assertEquals(0, map.size());
    }

}

