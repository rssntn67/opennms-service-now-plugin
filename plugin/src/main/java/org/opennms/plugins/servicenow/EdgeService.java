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
import org.opennms.integration.api.v1.model.TopologyEdge;
import org.opennms.integration.api.v1.model.TopologyPort;
import org.opennms.integration.api.v1.model.TopologyProtocol;
import org.opennms.integration.api.v1.model.TopologySegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    protected class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        String source;
        String target;

        @Override
        public void visitSource(Node node) {
            LOG.info("EdgeServiceVisitor:visitSource:Node {}", node);
            source = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(Node node) {
            LOG.info("EdgeServiceVisitor:visitTarget:Node {}", node);
            target = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitSource(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
            source = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
            target = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologySegment {}", segment);
        }

        public void clean() {
            source=null;
            target=null;
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(EdgeService.class);
    private final NodeDao nodeDao;
    private final EdgeDao edgeDao;
    private final String context;
    private final String parentKey;
    private final String gatewayKey;
    private final String excludedForeignSource;
    private final long initialDelayL;
    private final long delayL;

    private ScheduledFuture<?> scheduledFuture;

    private volatile Map<String, String> parentMap;


    private final Integer maxIteration;

    public void init() {
        parentMap = new ConcurrentHashMap<>();
        LOG.info("EdgeService init: parentMap initialized: {}", this.parentMap != null);
        LOG.info("EdgeService init: parentMap size: {}", this.parentMap.size());
        LOG.info("EdgeService init: parentMap class: {}", this.parentMap.getClass().getName());
        LOG.info("EdgeService init: this reference: {}", this);
        initScheduler();
    }

    private void initScheduler() {
        ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor();
        scheduledFuture =
                scheduledExecutorService
                        .scheduleWithFixedDelay(
                        this,
                                initialDelayL,
                                delayL,
                                TimeUnit.MILLISECONDS
                        );
        LOG.info("EdgeService init: Scheduler initialized, parentMap: {}", this.parentMap.size());
    }

    public EdgeService(final EdgeDao edgeDao,
                       final NodeDao nodeDao,
                       final String initialDelay,
                       final String delay,
                       final String maxIteration,
                       final String context,
                       final String parentKey,
                       final String gatewayKey,
                       final String excludedForeignSource) {

        this.edgeDao = Objects.requireNonNull(edgeDao);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.context = Objects.requireNonNull(context);
        this.parentKey = Objects.requireNonNull(parentKey);
        this.gatewayKey = Objects.requireNonNull(gatewayKey);
        this.maxIteration = Objects.requireNonNull(Integer.valueOf(maxIteration));
        this.excludedForeignSource = Objects.requireNonNull(excludedForeignSource);
        this.initialDelayL =  Long.parseLong(Objects.requireNonNull(initialDelay));
        this.delayL =  Long.parseLong(Objects.requireNonNull(delay));
    }

    public String getParentalNodeLabelById(int nodeid) {
        return getParentalNodeLabel(nodeDao.getNodeById(nodeid));
    }

    public String getParentalNodeLabel(Node node) {
        for (MetaData m : node.getMetaData()) {
            if (m.getContext().equals(context) && m.getKey().equals(this.parentKey)) {
                return m.getValue();
            }
        }
        if (this.parentMap.containsKey(node.getLabel()))
            return this.parentMap.get(node.getLabel());
        return "NoParentNodeFound";
    }


    @Override
    public void run() {

        Map<String, String> nodeGatewayMap =
                getNodeGatewayMap(nodeDao.getNodes());
        LOG.info("run: node to gateway map size: {}", nodeGatewayMap.size());

        Map<String, String> map =
                runParentDiscovery(
                        getEdgeMap(edgeDao.getEdges(TopologyProtocol.LLDP)),
                        nodeGatewayMap);
        LOG.info("run: lldp parent map size: {}", map.size());
        this.parentMap.clear();
        this.parentMap.putAll(map);
        Map<String, Set<String>> bridgeEdgeMap = getEdgeMap(edgeDao.getEdges(TopologyProtocol.BRIDGE));
        LOG.info("run: {} bridge map size: {}", TopologyProtocol.BRIDGE, bridgeEdgeMap.size());
        //runParentDiscovery(bridgeEdgeMap, nodeGatewayMap);

        LOG.info("run: parentMap: {}", this.parentMap.size());
    }


    protected Map<String,String> getNodeGatewayMap(
            final List<Node> nodes) {
        final Map<String,String> mappingNodeLabelToGateway= new HashMap<>();
        for (Node node : nodes) {
            String gatewayIp = node.getMetaData().stream()
                    .filter(m -> m.getContext().equals(context) && m.getKey().equals(gatewayKey))
                    .map(MetaData::getValue)
                    .findFirst()
                    .orElse(null);
            if (gatewayIp == null) {
                continue;
            }
            mappingNodeLabelToGateway.put(node.getLabel(), gatewayIp);
        }
        Map<String,String> mappingGatewayIpToGatewayNodeLabel = new HashMap<>();
        nodes.stream()
            .filter(n -> !n.getForeignSource().equals(excludedForeignSource))
            .forEach(node -> {

                String gatewayIp = node.getIpInterfaces().stream()
                        .filter(ipInterface -> mappingNodeLabelToGateway.containsValue(ipInterface.getIpAddress().getHostName()))
                        .map(ipInterface -> ipInterface.getIpAddress().getHostName())
                        .findFirst().orElse(null)
                        ;
                if (gatewayIp == null || mappingGatewayIpToGatewayNodeLabel.containsKey(gatewayIp)) {
                    return;
                }
                mappingGatewayIpToGatewayNodeLabel.put(gatewayIp, node.getLabel());
            });

        final Map<String, String> nodeGatewayMap = new HashMap<>();
        for (Map.Entry<String,String> entry: mappingNodeLabelToGateway.entrySet()) {
            if (mappingGatewayIpToGatewayNodeLabel.containsKey(entry.getValue())) {
                nodeGatewayMap.put(entry.getKey(), mappingGatewayIpToGatewayNodeLabel.get(entry.getValue()));
            }
        }

        return nodeGatewayMap;
    }

    protected Map<String, Set<String>> getEdgeMap(final Set<TopologyEdge> edges) {
        EdgeService.EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        final Map<String, Set<String>> map = new HashMap<>();
        for (TopologyEdge edge: edges) {
            visitor.clean();
            edge.visitEndpoints(visitor);

            if (visitor.source == null || visitor.target == null) {
                LOG.warn("getEdgeMap: parent or child is null");
                continue;
            }
            map.computeIfAbsent(visitor.source, k -> new HashSet<>()).add(visitor.target);
            map.computeIfAbsent(visitor.target, k -> new HashSet<>()).add(visitor.source);
        }
        return map;
    }

    protected Map<String,String> runParentDiscovery(
            final Map<String,Set<String>>edgeMap,
            final Map<String,String> nodeGatewayMap
            ) {
        if (edgeMap == null) {
            LOG.warn("runParentDiscovery: edgeMap is null");
            return new HashMap<>();
        }
        if (edgeMap.isEmpty()) {
            LOG.warn("runParentDiscovery: edgeMap is empty");
            return new HashMap<>();
        }

        if (nodeGatewayMap == null) {
            LOG.warn("runParentDiscovery: nodeGatewayMap is null");
            return new HashMap<>();
        }
        if (nodeGatewayMap.isEmpty()) {
            LOG.warn("runParentDiscovery: nodeGatewayMap is empty");
            return new HashMap<>();
        }

        Map<String,String> map = new HashMap<>();

        for (String child: nodeGatewayMap.keySet()) {
            String gateway = nodeGatewayMap.get(child);
            LOG.debug("runParentDiscovery: parsing {}: with gateway: {}", child, gateway);
            int i=0;
            Set<String> parents = new HashSet<>(List.of(gateway));
            while (!map.containsKey(child) && i< maxIteration) {
                LOG.debug("runParentDiscovery: iteration {}: checking if children of: {}", i,parents);
                parents = checkParent(map, edgeMap,gateway, child, parents, nodeGatewayMap);
                i++;
            }
        }
        return map;
    }

    private Set<String> checkParent(final Map<String, String> map, final Map<String,Set<String>>linkMap , String gateway, String child, Set<String> parents, Map<String,String> nodeGatewayMap) {
        final Set<String> children = new HashSet<>();
        parents.stream()
                .filter(level -> level.equals(gateway) || gateway.equals(nodeGatewayMap.get(level)))
                .forEach(level -> {
                    children.addAll(linkMap.get(level));
                    if (linkMap.get(level).contains(child)) {
                        LOG.debug("checkParent: child: {}: found parent: {}", child,level);
                        map.put(child, level);
                    }
                });
        return children;
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
