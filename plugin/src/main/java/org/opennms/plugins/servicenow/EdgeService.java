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
import org.opennms.integration.api.v1.topology.TopologyEdgeConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class EdgeService implements Runnable, HealthCheck , TopologyEdgeConsumer {

    private static class EdgeServiceVisitor implements TopologyEdge.EndpointVisitor {
        String parent;
        String child;
        final String filter;

        private EdgeServiceVisitor(String filter) {
            this.filter = filter;
        }

        @Override
        public void visitSource(Node node) {
            LOG.info("EdgeServiceVisitor:visitSource:Node {}", node);
            if (node.getCategories().contains(filter))
                child = node.getLabel();
        }

        @Override
        public void visitTarget(Node node) {
            LOG.info("EdgeServiceVisitor:visitTarget:Node {}", node);
            parent = node.getLabel();
        }

        @Override
        public void visitSource(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitSource:TopologyPort {}", port);
        }

        @Override
        public void visitTarget(TopologyPort port) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologyPort {}", port);
        }

        @Override
        public void visitTarget(TopologySegment segment) {
            LOG.info("EdgeServiceVisitor:visitTarget:TopologySegment {}", segment);
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
    private final String gatewayKey;
    private final String filter;

    private final ScheduledFuture<?> scheduledFuture;

    Map<String, String> parentMap = new HashMap<>();

    public EdgeService(EdgeDao edgeDao, NodeDao nodeDao, long initialDelay, long delay, String filter, String context, String parentKey, String gatewayKey) {
        this.edgeDao = Objects.requireNonNull(edgeDao);
        this.nodeDao = Objects.requireNonNull(nodeDao);
        this.context = Objects.requireNonNull(context);
        this.parentKey = Objects.requireNonNull(parentKey);
        this.gatewayKey = Objects.requireNonNull(gatewayKey);
        this.filter = Objects.requireNonNull(filter);

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this, initialDelay, delay, TimeUnit.MILLISECONDS);

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
        for (MetaData m : node.getMetaData()) {
            if (m.getContext().equals(context) && m.getKey().equals(gatewayKey)) {
                return parentMap.get(node.getLabel());
            }
        }

        return "NoParentNodeFound";
    }

    @Override
    public void run() {
        Set<TopologyEdge> edges = edgeDao.getEdges();
        TopologyEdge edge = edges.iterator().next();
        EdgeServiceVisitor visitor = new EdgeServiceVisitor(filter);
        edge.visitEndpoints(visitor);
        if (visitor.getChild() != null) {
            parentMap.put(visitor.child, visitor.parent);
        }
    }

    @Override
    public String getDescription() {
        return "Service Now Edge Service";
    }

    @Override
    public Response perform(Context context) throws Exception {
        return ImmutableResponse.newBuilder()
                .setStatus(scheduledFuture.isDone() ? Status.Failure : Status.Success)
                .setMessage(scheduledFuture.isDone() ? "Not running" : "Running")
                .build();
    }

    @Override
    public void onEdgeAddedOrUpdated(TopologyEdge topologyEdge) {
    }

    @Override
    public void onEdgeDeleted(TopologyEdge topologyEdge) {

    }

    @Override
    public Set<TopologyProtocol> getProtocols() {
        return Set.of(edgeDao.getProtocols().toArray(new TopologyProtocol[0]));
    }
}
