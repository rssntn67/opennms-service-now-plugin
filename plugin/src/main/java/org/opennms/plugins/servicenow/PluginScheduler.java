package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.health.Context;
import org.opennms.integration.api.v1.health.HealthCheck;
import org.opennms.integration.api.v1.health.Response;
import org.opennms.integration.api.v1.health.Status;
import org.opennms.integration.api.v1.health.immutables.ImmutableResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PluginScheduler implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(PluginScheduler.class);

    private final EdgeService edgeService;
    private final AssetForwarder assetForwarder;
    private final long initialDelayL;
    private final long delayL;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> edgeFuture;
    private ScheduledFuture<?> assetFuture;

    public PluginScheduler(EdgeService edgeService,
                           AssetForwarder assetForwarder,
                           String initialDelay,
                           String delay) {
        this.edgeService = edgeService;
        this.assetForwarder = assetForwarder;
        this.initialDelayL = Long.parseLong(initialDelay);
        this.delayL = Long.parseLong(delay);
    }

    public void init() {
        LOG.info("PluginScheduler: starting scheduler with initialDelay={} delay={}", initialDelayL, delayL);
        executor = Executors.newScheduledThreadPool(2);
        edgeFuture = executor.scheduleWithFixedDelay(edgeService, initialDelayL, delayL, TimeUnit.MILLISECONDS);
        assetFuture = executor.scheduleWithFixedDelay(assetForwarder, initialDelayL, delayL, TimeUnit.MILLISECONDS);
        LOG.info("PluginScheduler: scheduler started");
    }

    public void destroy() {
        LOG.info("PluginScheduler: shutting down");
        if (edgeFuture != null) {
            edgeFuture.cancel(true);
        }
        if (assetFuture != null) {
            assetFuture.cancel(true);
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Override
    public String getDescription() {
        return "Service Now Plugin Scheduler";
    }

    @Override
    public Response perform(Context context) {
        boolean done = (edgeFuture != null && edgeFuture.isDone())
                || (assetFuture != null && assetFuture.isDone());
        return ImmutableResponse.newBuilder()
                .setStatus(done ? Status.Failure : Status.Success)
                .setMessage(done ? "Not running" : "Running")
                .build();
    }
}
