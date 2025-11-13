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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EdgeService implements Runnable, HealthCheck {

    private class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        String parent;
        String child;

        @Override
        public void visitSource(Node node) {
            LOG.debug("EdgeServiceVisitor:visitSource:Node {}", node);
            parent = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();
        }

        @Override
        public void visitSource(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
            NodeCriteria nodeCriteria = port.getNodeCriteria();
            Node node = nodeDao.getNodeByForeignSourceAndForeignId(nodeCriteria.getForeignSource(),nodeCriteria.getForeignId());
            parent = node.getLabel();
        }

        @Override
        public void visitTarget(Node node) {
            LOG.debug("EdgeServiceVisitor:visitTarget:Node {}", node);
            parent = nodeDao.getNodeByForeignSourceAndForeignId(node.getForeignSource(), node.getForeignId()).getLabel();

        }


        @Override
        public void visitTarget(TopologyPort port) {
            LOG.debug("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
            NodeCriteria nodeCriteria = port.getNodeCriteria();
            Node node = nodeDao.getNodeByForeignSourceAndForeignId(nodeCriteria.getForeignSource(),nodeCriteria.getForeignId());
            child = node.getLabel();
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.debug("EdgeServiceVisitor:visitTarget:TopologySegment {}", segment);
        }

        public void clean() {
            parent=null;
            child=null;
        }
        public String getParent() {
            return parent;
        }

        public String getChild() {
            return child;
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(EdgeService.class);
    private final NodeDao nodeDao;
    private final EdgeDao edgeDao;
    private final String context;
    private final String parentKey;

    private final ScheduledFuture<?> scheduledFuture;

    private final Map<String, String> parentMap = new HashMap<>();

    public EdgeService(EdgeDao edgeDao,
                       NodeDao nodeDao,
                       String initialDelay,
                       String delay,
                       String context,
                       String parentKey) {
        this.edgeDao = Objects.requireNonNull(edgeDao);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.context = Objects.requireNonNull(context);
        this.parentKey = Objects.requireNonNull(parentKey);

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
        Set<String> gateway = new HashSet<>();
        nodeDao.getNodes().forEach( n -> {
            n.getMetaData().stream().filter(m -> m.getContext().equals(context) && m.getKey() == "gateway")
                    .forEach(m -> gateway.add(m.getValue()));
        });
        LOG.info("run: gateway: {}", gateway);
        parentMap.clear();
        final EdgeServiceVisitor visitor = new EdgeServiceVisitor();
        edgeDao.getEdges(TopologyProtocol.LLDP)
                .forEach( edge -> {
                    edge.visitEndpoints(visitor);
                    if (visitor.getChild() != null) {
                        parentMap.put(visitor.getChild(), visitor.getParent());
                    }
                    visitor.clean();
                });
        LOG.info("run: parentMap size: {}", parentMap.size());
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
