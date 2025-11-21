package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.InterfaceToNodeCache;
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.stream.Collectors;

public class EdgeService implements Runnable, HealthCheck {

    protected class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        String source;
        String target;

        @Override
        public void visitSource(Node node) {
            LOG.debug("EdgeServiceVisitor:visitSource:Node {}", node);
            source = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(Node node) {
            LOG.debug("EdgeServiceVisitor:visitTarget:Node {}", node);
            target = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitSource(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
            source = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
            target = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.debug("EdgeServiceVisitor:visitTarget:TopologySegment {}", segment);
        }

        public void clean() {
            source=null;
            target=null;
        }

    }

    private final Map<String,String> nodeLabelToGatewayMap = new ConcurrentHashMap<>();
    private final Map<String,String> gatewayToGatewayLabelMap = new ConcurrentHashMap<>();
    private final Map<TopologyProtocol, Map<String,Set<String>>> edgeMap = new ConcurrentHashMap<>();
    private volatile Map<String, String> parentByGatewayKeyMap;

    private final List<Node> nodes = Collections.synchronizedList(new ArrayList<>());
    private static final Logger LOG = LoggerFactory.getLogger(EdgeService.class);
    private final NodeDao nodeDao;
    private final EdgeDao edgeDao;
    private final InterfaceToNodeCache interfaceToNodeCache;
    private final String context;
    private final String parentKey;
    private final String gatewayKey;
    private final String excludedForeignSource;
    private final long initialDelayL;
    private final long delayL;

    private ScheduledFuture<?> scheduledFuture;


    private final Integer maxIteration;

    public void init() {
        parentByGatewayKeyMap = new ConcurrentHashMap<>();
        LOG.info("EdgeService init: parentMap initialized: {}", this.parentByGatewayKeyMap != null);
        LOG.info("EdgeService init: parentMap size: {}", this.parentByGatewayKeyMap.size());
        LOG.info("EdgeService init: parentMap class: {}", this.parentByGatewayKeyMap.getClass().getName());
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
        LOG.info("EdgeService init: Scheduler initialized, parentMap: {}", this.parentByGatewayKeyMap.size());
    }

    public EdgeService(final EdgeDao edgeDao,
                       final NodeDao nodeDao,
                       final InterfaceToNodeCache interfaceToNodeCache,
                       final String initialDelay,
                       final String delay,
                       final String maxIteration,
                       final String context,
                       final String parentKey,
                       final String gatewayKey,
                       final String excludedForeignSource) {

        this.edgeDao = Objects.requireNonNull(edgeDao);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.interfaceToNodeCache = Objects.requireNonNull(interfaceToNodeCache);
        this.context = Objects.requireNonNull(context);
        this.parentKey = Objects.requireNonNull(parentKey);
        this.gatewayKey = Objects.requireNonNull(gatewayKey);
        this.maxIteration = Objects.requireNonNull(Integer.valueOf(maxIteration));
        this.excludedForeignSource = Objects.requireNonNull(excludedForeignSource);
        this.initialDelayL =  Long.parseLong(Objects.requireNonNull(initialDelay));
        this.delayL =  Long.parseLong(Objects.requireNonNull(delay));
    }

    public Set<String> getGateways() {
        return new HashSet<>(nodeLabelToGatewayMap.values());
    }

    public Map<String, String> getGatewayToGatewayLabelMap() {
        return gatewayToGatewayLabelMap;
    }

    public String getGatewayLabel(String ip) {
        if (gatewayToGatewayLabelMap.containsKey(ip)) {
            return gatewayToGatewayLabelMap.get(ip);
        }
        return "noGatewayLabelFound";
    }

    public Map<String, String> getParentByGatewayKeyMap() {
        return parentByGatewayKeyMap;
    }

    public String getParentByParentKey(Node node) {
        for (MetaData m : node.getMetaData()) {
            if (m.getContext().equals(this.context) && m.getKey().equals(this.parentKey)) {
                LOG.info("getParent: found parent: {}, for node: {}", m.getValue(), node.getLabel() );
                return m.getValue();
            }
        }
        return null;
    }

    public String getParentByParentKey(int nodeId) {
        return getParentByParentKey(nodeDao.getNodeById(nodeId));
    }

    public String getParentByGatewayKey(Node node) {
        if (this.parentByGatewayKeyMap.containsKey(node.getLabel()))
            return this.parentByGatewayKeyMap.get(node.getLabel());
        return "NoParentNodeFound";
    }

    public String getParentByGatewayKey(int nodeId) {
        return getParentByGatewayKey(nodeDao.getNodeById(nodeId));
    }

    public Map<String, String> runLabelToGatewayLabelMap() {
        final Map<String, String> map = new HashMap<>();
        for (Map.Entry<String,String> entry: nodeLabelToGatewayMap.entrySet()) {
            if (gatewayToGatewayLabelMap.containsKey(entry.getValue())) {
                map.put(entry.getKey(), gatewayToGatewayLabelMap.get(entry.getValue()));
            }
        }
        LOG.debug("run: nodeGatewayMap: {}", map);
        return map;
    }

    @Override
    public void run() {
        this.nodes.clear();
        this.nodes.addAll(nodeDao.getNodes());

        this.nodeLabelToGatewayMap.clear();
        this.nodeLabelToGatewayMap.putAll(runNodeLabelToGatewayMap(this.nodes));

        gatewayToGatewayLabelMap.clear();
        gatewayToGatewayLabelMap.putAll(runGatewayToGatewayLabelMap(nodeDao.getDefaultLocationName(),new HashSet<>(nodeLabelToGatewayMap.values())));
        LOG.debug("run: mappingGatewayIpToGatewayNodeLabel: {}", gatewayToGatewayLabelMap);

        edgeMap.put(TopologyProtocol.LLDP, runEdgeMap(edgeDao.getEdges(TopologyProtocol.LLDP)));
        LOG.debug("run: edge lldp map size: {}", edgeMap.get(TopologyProtocol.LLDP).size() );
        Map<String, String> map =
                runParentDiscovery(
                    edgeMap.get(TopologyProtocol.LLDP),
                    runLabelToGatewayLabelMap());
        LOG.info("run: lldp parent map size: {}", map.size());
        this.parentByGatewayKeyMap.clear();
        this.parentByGatewayKeyMap.putAll(map);

        LOG.info("run: parentMap: {}", this.parentByGatewayKeyMap.size());
    }

    public Set<String> getLocations() {
        return this.nodes.stream().map(Node::getLocation).collect(Collectors.toSet());
    }

    public Map<String, String> runNodeLabelToGatewayMap(List<Node> nodes) {
        Map<String, String> map = new HashMap<>();
        nodes.forEach(node -> {
            LOG.debug("run: parsing: label: {}, location: {}", node.getLabel(), node.getLocation());
            String gatewayIp = node.getMetaData().stream()
                    .filter(m -> m.getContext().equals(context) && m.getKey().equals(gatewayKey))
                    .map(MetaData::getValue)
                    .findFirst()
                    .orElse(null);
            if (gatewayIp != null) {
                LOG.debug("run: found: {}", gatewayIp);
                map.put(node.getLabel(), gatewayIp);
            }
        });
        LOG.debug("run: mappingNodeLabelToGateway size: {}", map.size());
        return map;
    }

    public Map<String, String> runGatewayToGatewayLabelMap(String location, Set<String> gateways) {
        return gateways
                .stream()
                .collect(Collectors.toMap(gateway -> gateway,gateway ->runGatewayToGatewayLabel(location, gateway)));
    }

    public String runGatewayToGatewayLabel(String onmsLocation, String gateway) {
        LOG.debug("run: try to get label for gateway: {} and location: {}", gateway, onmsLocation);
        InetAddress gwIp;
        try {
            gwIp = InetAddress.getByName(gateway);
        } catch (UnknownHostException e) {
            LOG.warn("run: error in gateway string: {}", gateway, e);
            return null;
        }

        LOG.debug("run: try to get label for location: {}", onmsLocation );

        Integer nodeId = interfaceToNodeCache.getFirstNodeId(onmsLocation, gwIp).orElse(0);
        LOG.debug("run: get NodeId: {}", nodeId );
        if (nodeId > 0) {
            Node node = nodeDao.getNodeById(nodeId);
            LOG.debug("run: got Node: {}, FS {}", node.getLabel(), node.getForeignSource());
            LOG.debug("run: checking FS {}: against excluded: {}", node.getForeignSource(), excludedForeignSource);
            if (!node.getForeignSource().equals(excludedForeignSource)) {
                LOG.debug("run: mappingGatewayIpToGatewayNodeLabel adding: {}, {}", gateway, node.getLabel());
                return node.getLabel();
            }
        }
        return null;
    }

    public Map<String, Set<String>> runEdgeMap(Set<TopologyEdge> edges) {
        EdgeService.EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        final Map<String, Set<String>> map = new HashMap<>();
        edges.forEach(edge -> {
            visitor.clean();
            edge.visitEndpoints(visitor);

            if (visitor.source != null && visitor.target != null) {
                map.computeIfAbsent(visitor.source, k -> new HashSet<>()).add(visitor.target);
                map.computeIfAbsent(visitor.target, k -> new HashSet<>()).add(visitor.source);
            }
        });
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
            if (!edgeMap.containsKey(child)) {
                LOG.debug("runParentDiscovery: no edges found for {}", child);
                continue;
            }
            Set<String> parents = new HashSet<>(List.of(gateway));
            while (!parents.isEmpty() &&!map.containsKey(child) && i< maxIteration) {
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
