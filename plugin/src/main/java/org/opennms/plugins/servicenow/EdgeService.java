package org.opennms.plugins.servicenow;

import kotlin.Pair;
import org.opennms.integration.api.v1.dao.EdgeDao;
import org.opennms.integration.api.v1.dao.InterfaceToNodeCache;
import org.opennms.integration.api.v1.dao.NodeDao;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EdgeService implements Runnable {

    protected static Pair<Integer, Integer> getFromId(String id) throws RuntimeException {
        if (!id.startsWith("s:")) {
            throw new RuntimeException("Error: " + id + "not a Shared segment");
        }
        List<String> values = Arrays.stream(id.replace("s:", "").split("\\|")).toList();
        String source = values.get(0);
        Integer sourceId = Integer.parseInt(Arrays.stream(source.split(":")).toList().get(0));
        String target = values.get(1);
        if (target.startsWith("m:")) {
            throw new RuntimeException("Error: " + id + " target has no identified nodes");
        }
        Integer targetId = Integer.parseInt(Arrays.stream(target.split(":")).toList().get(0));
        return new Pair<>(sourceId, targetId);
    }

    public Map<String, String> getParentByParentKeyMap() {
        return this.nodes.stream()
                .collect(HashMap::new,
                        (map, n) -> {
                            String parent = getParent(n);
                            if (parent != null) {
                                map.put(n.getLabel(), parent);
                            }
                        },
                        HashMap::putAll);
    }

    protected class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        String source;
        String target;
        String id;

        public void setId(String id) {
            this.id = id;
        }

        @Override
        public void visitSource(Node node) {
            LOG.info("->{}:visitSourceNode {}",id, node);
            source = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(Node node) {
            LOG.info("->{}:visitTarget:Node {}",id, node);
            target = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitSource(TopologyPort port) {
            LOG.info("->{}:visitSource:TopologyPort {}",id, port);
            source = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.info("->{}:visitTarget:TopologyPort {}",id, port);
            target = nodeDao.getNodeByForeignSourceAndForeignId(port.getNodeCriteria().getForeignSource(),port.getNodeCriteria().getForeignId()).getLabel();
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.info("->{}:visitTarget:TopologySegment:Criteria-> {}",id, segment.getSegmentCriteria());
            try {
                Pair<Integer, Integer> pair = EdgeService.getFromId(id);
                source = nodeDao.getNodeById(pair.getFirst()).getLabel();
                target = nodeDao.getNodeById(pair.getSecond()).getLabel();
            } catch (Exception e) {
                LOG.info("->{}:visitTarget:TopologySegment: {}", id, e.getMessage());
            }

        }

        public void clean() {
            source=null;
            target=null;
            id=null;
        }

    }

    private final List<Node> nodes = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> locations = Collections.synchronizedSet(new HashSet<>());

    private final Map<String, Set<String>> gatewayToChildMap = new ConcurrentHashMap<>();
    private final Map<String,String> gatewayToGatewayLabelMap = new ConcurrentHashMap<>();
    private final Map<TopologyProtocol, Map<String,Set<String>>> edgeMap = new ConcurrentHashMap<>();
    private volatile Map<String, String> parentByGatewayKeyMap;

    private static final Logger LOG = LoggerFactory.getLogger(EdgeService.class);
    private final NodeDao nodeDao;
    private final EdgeDao edgeDao;
    private final InterfaceToNodeCache interfaceToNodeCache;
    private final String context;
    private final String parentKey;
    private final String gatewayKey;
    private final String excludedForeignSource;

    private final Integer maxIteration;

    public void init() {
        parentByGatewayKeyMap = new ConcurrentHashMap<>();
        LOG.info("init: parentMap initialized: {}", this.parentByGatewayKeyMap != null);
        LOG.info("init: parentMap size: {}", this.parentByGatewayKeyMap.size());
        LOG.info("init: parentMap class: {}", this.parentByGatewayKeyMap.getClass().getName());
        LOG.info("init: this reference: {}", this);
    }


    public EdgeService(final EdgeDao edgeDao,
                       final NodeDao nodeDao,
                       final InterfaceToNodeCache interfaceToNodeCache,
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
    }
    public Set<String> getLocations() {
        return new HashSet<>(this.locations);
    }

    public Set<String> getEdges(TopologyProtocol protocol, String label) {
        if (edgeMap.get(protocol).containsKey(label)) {
            return new HashSet<>(edgeMap.get(protocol).get(label));
        }
        return new HashSet<>();
    }

    public Set<String> getGateways() {
        return new HashSet<>(gatewayToChildMap.keySet());
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
                LOG.info("getParentByParentKey: found parent: {}, for node: {}", m.getValue(), node.getLabel() );
                return m.getValue();
            }
        }
        LOG.info("getParentByParentKey: no parent found: for node: {}", node.getLabel() );
        return null;
    }

    public String getParentByParentKey(int nodeId) {
        return getParentByParentKey(nodeDao.getNodeById(nodeId));
    }

    public String getParentByGatewayKey(Node node) {
        if (this.parentByGatewayKeyMap.containsKey(node.getLabel())) {
            LOG.info("getParentByGatewayKey: found parent: {}, for node: {}", this.parentByGatewayKeyMap.get(node.getLabel()), node.getLabel() );
            return this.parentByGatewayKeyMap.get(node.getLabel());
        }
        LOG.info("getParentByGatewayKey: no parent found: for node: {}", node.getLabel() );
        return "NoParentNodeFound";
    }

    public String getParentByGatewayKey(int nodeId) {
        return getParentByGatewayKey(nodeDao.getNodeById(nodeId));
    }

    public String getParent(int nodeId) {
        return getParent(nodeDao.getNodeById(nodeId));
    }

    public String getParent(final Node node) {
        String parent = getParentByParentKey(node);
        if (parent == null) {
            parent = getParentByGatewayKey(node);
        }
        return parent;
    }


    public Map<String, Set<String>> populateGatewayLabelToSetLabelMap() {
        final Map<String, Set<String>> map = new HashMap<>();
        for (Map.Entry<String,Set<String>> entry: gatewayToChildMap.entrySet()) {
            if (gatewayToGatewayLabelMap.containsKey(entry.getKey())) {
                map.put(gatewayToGatewayLabelMap.get(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    @Override
    public void run() {
        this.nodes.clear();
        this.nodes.addAll(nodeDao.getNodes());
        LOG.info("run: nodes size: {}", nodes.size());
        this.gatewayToChildMap.clear();
        this.gatewayToChildMap.putAll(populateGatewayMap(this.nodes));
        LOG.info("run: gatewayToChildMap size: {}", gatewayToChildMap.size());

        locations.clear();
        locations.addAll(populateLocations(nodes));
        LOG.info("run: locations size: {}", locations.size());

        gatewayToGatewayLabelMap.clear();
        gatewayToGatewayLabelMap.putAll(populateGatewayToGatewayLabelMap(this.locations, new HashSet<>(gatewayToChildMap.keySet())));
        LOG.info("run: gatewayToGatewayLabelMap: {}", gatewayToGatewayLabelMap.size());

        Map<String, Set<String>> gatewayMap = populateGatewayLabelToSetLabelMap();
        LOG.info("run: gatewayMap size: {}", gatewayMap.size());

        //LLDP
        Set<TopologyEdge> lldpEdges = edgeDao.getEdges(TopologyProtocol.LLDP);
        LOG.info("run: lldpEdges size: {}", lldpEdges.size());
        Map<String, Set<String>> lldpEdgeMap = populateEdgeMap(lldpEdges);
        LOG.info("run: lldpEdgeMap size: {}", lldpEdgeMap.size());
        edgeMap.remove(TopologyProtocol.LLDP);
        edgeMap.put(TopologyProtocol.LLDP, lldpEdgeMap);
        Map<String, String> lldpParentMap =
                runDiscovery(
                    lldpEdgeMap,
                    gatewayMap
                );
        LOG.info("run: found lldp parent map of size: {}", lldpParentMap.size());

        //CDP
        Set<TopologyEdge> cdpEdges = edgeDao.getEdges(TopologyProtocol.CDP);
        LOG.info("run: cdpEdges size: {}", cdpEdges.size());
        Map<String, Set<String>> cdpEdgeMap = populateEdgeMap(cdpEdges);
        LOG.info("run:cdpEdgeMap size: {}", lldpEdgeMap.size());
        edgeMap.remove(TopologyProtocol.CDP);
        edgeMap.put(TopologyProtocol.CDP, cdpEdgeMap);
        Map<String, String> cdpParentMap =
                runDiscovery(
                        cdpEdgeMap,
                        gatewayMap
                );
        LOG.info("run: found cdp parent map of size: {}", cdpParentMap.size());

        //BRIDGE
        Set<TopologyEdge> bridgeEdges = edgeDao.getEdges(TopologyProtocol.BRIDGE);
        LOG.info("run: bridgeEdges size: {}", lldpEdges.size());
        Map<String, Set<String>> bridgeEdgeMap = populateEdgeMap(bridgeEdges);
        LOG.info("run: bridgeEdgeMap size: {}", bridgeEdgeMap.size());
        edgeMap.remove(TopologyProtocol.BRIDGE);
        edgeMap.put(TopologyProtocol.BRIDGE, lldpEdgeMap);
        Map<String, String> bridgeParentMap =
                runDiscovery(
                        bridgeEdgeMap,
                        gatewayMap
                );
        LOG.info("run: found bridge parent map of size: {}", bridgeParentMap.size());

        this.parentByGatewayKeyMap.clear();
        lldpParentMap.forEach((key, value) -> this.parentByGatewayKeyMap.putIfAbsent(key, value));
        LOG.info("run: added lldp: parentByGatewayMap {}", this.parentByGatewayKeyMap.size());
        cdpParentMap.forEach((key, value) -> this.parentByGatewayKeyMap.putIfAbsent(key, value));
        LOG.info("run: added cdp: parentByGatewayMap {}", this.parentByGatewayKeyMap.size());
        bridgeParentMap.forEach((key, value) -> this.parentByGatewayKeyMap.putIfAbsent(key, value));
        LOG.info("run: added bridge: parentByGatewayMap {}", this.parentByGatewayKeyMap.size());
        gatewayMap.forEach((parent, set) -> set.forEach(label -> this.parentByGatewayKeyMap.putIfAbsent(label,parent)));
        LOG.info("run: added gateways: parentByGatewayMap {}", this.parentByGatewayKeyMap.size());
    }

    public Set<String> populateLocations(List<Node> nodes) {
        return nodes.stream().map(Node::getLocation).collect(Collectors.toSet());
    }

    public Map<String, Set<String>> populateGatewayMap(List<Node> nodes) {
        Map<String, Set<String>> map = new HashMap<>();
        nodes.forEach(node -> {
            LOG.debug("run: parsing: label: {}, location: {}", node.getLabel(), node.getLocation());
            String gatewayIp = node.getMetaData().stream()
                    .filter(m -> m.getContext().equals(context) && m.getKey().equals(gatewayKey))
                    .map(MetaData::getValue)
                    .findFirst()
                    .orElse(null);
            if (gatewayIp != null) {
                LOG.debug("run: found: {} for gateway: {}", node.getLabel(), gatewayIp);
                map.computeIfAbsent(gatewayIp, k -> new HashSet<>()).add(node.getLabel());
            }
        });
        LOG.debug("run: gateway map: {}", map);
        return map;
    }

    public Map<String, String> populateGatewayToGatewayLabelMap(Set<String> locations, Set<String> gateways) {
        Map<String, String> map = new HashMap<>();
  G:      for (String gateway: gateways) {
            for (String location: locations) {
                String gwLabel = findGatewayToGatewayLabel(location, gateway);
                if (gwLabel != null) {
                    map.put(gateway, gwLabel);
                    LOG.debug("run: gateway: {}, -> label: {}", gateway, gwLabel);
                    continue G;
                }
            }
            LOG.debug("run: gateway: {}, -> no node found", gateway);
        }
        return map;
    }

    public String findGatewayToGatewayLabel(String onmsLocation, String gateway) {
        LOG.debug("run: try to get label for gateway: {} and location: {}", gateway, onmsLocation);
        InetAddress gwIp;
        try {
            gwIp = InetAddress.getByName(gateway);
        } catch (UnknownHostException e) {
            LOG.warn("run: error in gateway string: {}", gateway, e);
            return null;
        }

        LOG.debug("run: try to get label for location: {}", onmsLocation );

        Integer nodeId = interfaceToNodeCache.getFirstNodeId(onmsLocation, gwIp).orElse(-1);
        if (nodeId > 0) {
            Node node = nodeDao.getNodeById(nodeId);
            LOG.debug("run: got Node: {}, FS {}", node.getLabel(), node.getForeignSource());
            LOG.debug("run: checking FS {}: against excluded: {}", node.getForeignSource(), excludedForeignSource);
            if (!node.getForeignSource().equals(excludedForeignSource)) {
                LOG.debug("run: mappingGatewayIpToGatewayNodeLabel adding: {}, {}", gateway, node.getLabel());
                return node.getLabel();
            }
        }
        LOG.debug("run: no valid NodeId {}, found for: {}: on location: {}", nodeId, gateway,onmsLocation );
        return null;
    }

    public Map<String, Set<String>> populateEdgeMap(Set<TopologyEdge> edges) {
        EdgeService.EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        final Map<String, Set<String>> map = new HashMap<>();
        edges.forEach(edge -> {
            visitor.clean();
            visitor.setId(edge.getId());
            edge.visitEndpoints(visitor);

            if (visitor.source != null && visitor.target != null) {
                map.computeIfAbsent(visitor.source, k -> new HashSet<>()).add(visitor.target);
                map.computeIfAbsent(visitor.target, k -> new HashSet<>()).add(visitor.source);
            }
        });
        return map;
    }

    protected Map<String,String> runDiscovery(
                final Map<String,Set<String>>edgeMap,
                final Map<String,Set<String>>gatewayMap
            ) {
        if (edgeMap == null) {
            LOG.warn("run: edgeMap is null");
            return new HashMap<>();
        }
        if (edgeMap.isEmpty()) {
            LOG.warn("run: edgeMap is empty");
            return new HashMap<>();
        }
        LOG.debug("run: edgeMap: {}", edgeMap);

        if (gatewayMap == null) {
            LOG.warn("run: gatewayMap is null");
            return new HashMap<>();
        }
        if (gatewayMap.isEmpty()) {
            LOG.warn("run: gatewayMap is empty");
            return new HashMap<>();
        }

        final Map<String,String> map = new HashMap<>();

        for (String gateway: gatewayMap.keySet()) {
            if (!edgeMap.containsKey(gateway)) {
                LOG.debug("run: no edges for gateway: {}", gateway);
                continue;
            }
            Set<String> children = gatewayMap.get(gateway);
            LOG.debug("run: parsing {}: with children: {}", gateway, children);
            int i=0;
            Set<String> parents = new HashSet<>(List.of(gateway));
            final Set<String> parsed = new HashSet<>();
            while (!parents.isEmpty() && !children.isEmpty()  && i< maxIteration) {
                LOG.debug("run: iteration {}: checking if children of: {}", i,parents);
                parents = checkParent(parsed, map, edgeMap, children, parents);
                i++;
            }
        }
        return map;
    }

    private Set<String> checkParent(final Set<String> parsed, final Map<String, String> map, final Map<String,Set<String>>edgeMap , Set<String> children, Set<String> parents) {
        final Set<String> downlevel = new HashSet<>();
        parents
                .forEach(level -> {
                    parsed.add(level);
                    LOG.debug("run: parsing: {}", level);
                    if (edgeMap.containsKey(level)) {
                        downlevel.addAll(edgeMap.get(level));
                    }
                    Set<String> levelEdges = new HashSet<>(edgeMap.get(level));
                    levelEdges.retainAll(children);
                    for (String child: levelEdges) {
                        LOG.debug("run: {} found child: {}", level, child);
                        map.put(child, level);
                    }
                    children.removeAll(levelEdges);
                });
        LOG.debug("run: parsed: {}", parsed);
        LOG.debug("run: children: {}", children);
        downlevel.removeAll(parsed);
        LOG.debug("run: parents: {}", downlevel);
        return downlevel;
    }

    public List<Node> getNodes() {
        return this.nodes;
    }

}
