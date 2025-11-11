package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.NodeDao;
import org.opennms.integration.api.v1.health.Context;
import org.opennms.integration.api.v1.health.HealthCheck;
import org.opennms.integration.api.v1.health.Response;
import org.opennms.integration.api.v1.health.Status;
import org.opennms.integration.api.v1.health.immutables.ImmutableResponse;
import org.opennms.integration.api.v1.model.MetaData;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.integration.api.v1.model.NodeCriteria;
import org.opennms.integration.api.v1.model.TopologyEdge;
import org.opennms.integration.api.v1.model.TopologyPort;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.integration.api.v1.model.TopologySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EdgeService implements Runnable, HealthCheck {

    private static class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        NodeCriteria parent;
        NodeCriteria child;

        @Override
        public void visitSource(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
            parent = port.getNodeCriteria();
        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
            child = port.getNodeCriteria();
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologySegment {}", segment);
        }

        public void clean() {
            parent=null;
            child=null;
        }
        public NodeCriteria getParent() {
            return parent;
        }

        public NodeCriteria getChild() {
            return child;
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(EdgeService.class);
    private final NodeDao nodeDao;
    private final EdgeDao edgeDao;
    private final String context;
    private final String parentKey;
    private final String gatewayKey;
    private final String excludedForeignSource;

    private final ScheduledFuture<?> scheduledFuture;

    public Map<String, String> getParentMap() {
        return new HashMap<>(parentMap);
    }

    private final Map<String, String> parentMap = new ConcurrentHashMap<>();

    private final Integer maxIteration;
    public EdgeService(EdgeDao edgeDao,
                       NodeDao nodeDao,
                       String initialDelay,
                       String delay,
                       String maxIteration,
                       String context,
                       String parentKey,
                       String gatewayKey,
                       String excludedForeignSource) {
        this.edgeDao = Objects.requireNonNull(edgeDao);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.context = Objects.requireNonNull(context);
        this.parentKey = Objects.requireNonNull(parentKey);
        this.gatewayKey = Objects.requireNonNull(gatewayKey);
        this.maxIteration = Objects.requireNonNull(Integer.valueOf(maxIteration));
        this.excludedForeignSource = Objects.requireNonNull(excludedForeignSource);
        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this, Long.parseLong(initialDelay),
                Long.parseLong(delay), TimeUnit.MILLISECONDS);

    }

    public String getParentalNodeLabelById(int nodeid) {
        return getParentalNodeLabel(nodeDao.getNodeById(nodeid));
    }

    public String getParentalNodeLabel(Node node) {
        for (MetaData m : node.getMetaData()) {
            if (m.getContext().equals(context) && m.getKey().equals(parentKey)) {
                return m.getValue();
            }
        }
        if (parentMap.containsKey(node.getLabel()))
            return parentMap.get(node.getLabel());
        return "NoParentNodeFound";
    }


    @Override
    public void run() {
        final List<Node> nodes = nodeDao.getNodes();
        final Set<TopologyEdge> edges = edgeDao.getEdges();
        final EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        parentMap.clear();
        runOverLldpProtocol(nodes, edges, visitor);
        runOverBridgeProtocol(edges, visitor);
    }

    private void runOverBridgeProtocol(Set<TopologyEdge> edges, EdgeServiceVisitor visitor) {
        edges.stream()
            .filter(e -> e.getProtocol() == TopologyProtocol.BRIDGE)
            .forEach(e -> {
                    visitor.clean();
                    e.visitEndpoints(visitor);
            });
    }

    private void runOverLldpProtocol(final List<Node> nodes, final Set<TopologyEdge> edges, final EdgeServiceVisitor visitor) {
        Map<String, String> nodeGatewayMap = new HashMap<>();

        for (Node node : nodes) {
            // Get gateway IP for this node
            String gatewayIp = node.getMetaData().stream()
                    .filter(m -> m.getContext().equals(context) && m.getKey().equals(gatewayKey))
                    .map(MetaData::getValue)
                    .findFirst()
                    .orElse(null);

            if (gatewayIp == null || nodeGatewayMap.containsValue(gatewayIp)) {
                continue;
            }
            try {
                InetAddress gatewayAddress = InetAddress.getByName(gatewayIp);

                // Find the node that has this gateway IP
                nodes.stream()
                        .filter(n -> !n.getForeignSource().equals(excludedForeignSource))
                        .filter(n -> n.getIpInterfaces().stream()
                                .anyMatch(ipInterface -> ipInterface.getIpAddress().equals(gatewayAddress)))
                        .map(Node::getLabel)
                        .findFirst().ifPresent(gatewayNodeLabel -> nodeGatewayMap.put(node.getLabel(), gatewayNodeLabel));

            } catch (UnknownHostException e) {
                LOG.warn("run: cannot parse gateway ip; {}", gatewayIp, e);
            }
        }
        LOG.info("run: node to gateway map: {}", nodeGatewayMap);
        final Map<String, Set<String>> edgeMap = edges
                .stream()
                .filter(e -> e.getProtocol() == TopologyProtocol.LLDP)
                .collect(HashMap::new,
                        (map, edge) -> {
                            edge.visitEndpoints(visitor);
                            String parent = getNodeLabel(visitor.getParent(), nodes);
                            String child = getNodeLabel(visitor.getChild(), nodes);

                            if (parent != null && child != null) {
                                map.computeIfAbsent(parent, k -> new HashSet<>()).add(child);
                                map.computeIfAbsent(child, k -> new HashSet<>()).add(parent);
                            }
                            visitor.clean();
                        },
                        Map::putAll
                );
        LOG.info("run: edgeMap: {}", edgeMap);

        runParentDiscovery(edgeMap, nodeGatewayMap);
    }

    public void runParentDiscovery(Map<String,Set<String>>edgeMap, Map<String,String> nodeGatewayMap) {
        LOG.debug("runParentDiscovery: edgeMap: {}", edgeMap);
        LOG.debug("runParentDiscovery: nodeGatewayMap: {}", nodeGatewayMap);

        for (String child: nodeGatewayMap.keySet()) {
            String gateway = nodeGatewayMap.get(child);
            LOG.debug("runTopologyDiscovery: parsing {}: with gateway: {}", child, gateway);
            int i=0;
            Set<String> parents = new HashSet<>(List.of(gateway));
            while (!parentMap.containsKey(child) && i< maxIteration) {
                LOG.debug("runTopologyDiscovery: iteration {}: checking if children of: {}", i,parents);
                parents = checkParent(edgeMap,gateway, child, parents, nodeGatewayMap);
                i++;
            }
        }

    }

    private Set<String> checkParent(Map<String,Set<String>>linkMap , String gateway, String child, Set<String> parents, Map<String,String> nodeGatewayMap) {
        final Set<String> children = new HashSet<>();
        parents.stream()
                .filter(level -> level.equals(gateway) || gateway.equals(nodeGatewayMap.get(level)))
                .forEach(level -> {
                    children.addAll(linkMap.get(level));
                    if (linkMap.get(level).contains(child)) {
                        LOG.debug("checkParent: child: {}: found parent: {}", child,level);
                        parentMap.put(child, level);
                    }
                });
        return children;
    }

    private static String getNodeLabel(NodeCriteria criteria, List<Node> nodes) {
        for (Node node: nodes) {
            if (node.getForeignSource().equals(criteria.getForeignSource()) &&
                    node.getForeignId().equals(criteria.getForeignId())) {
                return node.getLabel();
            }
        }
        return null;
    }

    @Override
    public String getDescription() {
        return "Service Now Edge Service";
    }

    @Override
    public Response perform(Context context) {
        return ImmutableResponse.newBuilder()
                .setStatus(scheduledFuture.isDone() ? Status.Failure : Status.Success)
                .setMessage(scheduledFuture.isDone() ? "Not running" : "Running")
                .build();
    }

    public void destroy() {
        LOG.debug("EdgeService is shutting down.");
        scheduledFuture.cancel(true);
    }

}
