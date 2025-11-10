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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EdgeService implements Runnable, HealthCheck {

    private static class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        NodeCriteria parent;
        NodeCriteria child;

        @Override
        public void visitSource(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
            parent = port.getNodeCriteria();
        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
            child = port.getNodeCriteria();
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

    private final Map<String, String> parentMap = new HashMap<>();

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

    public synchronized String getParentalNodeLabel(Node node) {
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
        final Map<String, InetAddress> nodeToGatewayIp = nodes.stream()
                .flatMap(node -> node.getMetaData().stream()
                        .filter(m -> m.getContext().equals(context) && m.getKey().equals(gatewayKey))
                        .map(m -> {
                            try {
                                InetAddress gateway = InetAddress.getByName(m.getValue());
                                return new AbstractMap.SimpleEntry<>( node.getLabel(),gateway);
                            } catch (UnknownHostException e) {
                                return null;
                            }
                        }).filter(Objects::nonNull))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue)
                );

        final Map<InetAddress, String> gatewayIpToNode = nodes.stream()
                .filter(n -> !n.getForeignSource().equals(excludedForeignSource))
                .flatMap(node ->
                        node.getIpInterfaces().stream()
                                .map(ipInterface -> new AbstractMap.SimpleEntry<>(ipInterface.getIpAddress(), node.getLabel()))
                                .filter(entry -> nodeToGatewayIp.containsValue(entry.getKey()))
                )
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing
                ));

        final EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        final Map<String, Set<String>> links = edgeDao.getEdges()
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
        parentMap.clear();
        //How to find the topology:

        for (String child: nodeToGatewayIp.keySet()) {
            String gateway = gatewayIpToNode.get(nodeToGatewayIp.get(child));
            Set<String> parents = links.get(gateway);
            if (parents.contains(child)) {
                parentMap.put(child,gateway);
                break;
            }
            int i=0;
            while (!parents.isEmpty() || i< maxIteration) {
                parents = checkParent(links,gateway, child, parents, nodeToGatewayIp,gatewayIpToNode);
                i++;
            }
        }

    }
    private Set<String> checkParent(Map<String,Set<String>>links , String gateway, String child, Set<String> parents, Map<String,InetAddress> nodeGateway, Map<InetAddress, String> ipGateway) {
        final Set<String> levels = new HashSet<>();
        parents.stream()
                .filter(nodeGateway::containsKey)
                .filter(level -> ipGateway.get(nodeGateway.get(level)).equals(gateway))
                .forEach(level -> {
                    levels.add(level);
                    if (links.get(level).contains(child)) {
                        parentMap.put(child, level);
                    }
                });
        if (parentMap.containsKey(child)) {
            return Collections.emptySet();
        }
        return levels;
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
