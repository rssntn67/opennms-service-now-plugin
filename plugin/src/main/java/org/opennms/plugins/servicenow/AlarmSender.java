package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.integration.api.v1.model.Node;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AlarmSender {
    private static final Logger LOG = LoggerFactory.getLogger(AlarmSender.class);

    private record AlarmNode(Alarm alarm, Node node, String parent) {
    }

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final PluginEventForwarder eventForwarder;
    private final int maxRetry;
    private final long retryDelay;
    private final long timeoutMs;

    private final LinkedBlockingQueue<AlarmNode> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private ExecutorService queueThread;
    private final ExecutorService sendThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "alarm-forwarder-send"));

    public AlarmSender(ConnectionManager connectionManager,
                       ApiClientProvider apiClientProvider,
                       PluginEventForwarder eventForwarder,
                       int maxRetry,
                       long retryDelay,
                       long timeoutMs) {
        this.connectionManager = connectionManager;
        this.apiClientProvider = apiClientProvider;
        this.eventForwarder = eventForwarder;
        this.maxRetry = maxRetry;
        this.retryDelay = retryDelay;
        this.timeoutMs = timeoutMs;
    }

    public void enqueue(Alarm alarm, Node node, String parent) {
        queue.offer(new AlarmNode(alarm, node, parent));
    }

    public void start() {
        running = true;
        queueThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "alarm-forwarder-queue"));
        queueThread.submit(this::processQueue);
        LOG.info("start: alarm sender started (timeoutMs={})", timeoutMs);
    }

    public void stop() {
        running = false;
        if (queueThread != null) {
            queueThread.shutdownNow();
        }
        sendThread.shutdownNow();
        LOG.info("stop: alarm sender stopped");
    }

    private void processQueue() {
        while (running) {
            try {
                AlarmNode alarmNode = queue.poll(1, TimeUnit.SECONDS);
                if (alarmNode == null) {
                    continue;
                }
                Future<?> future = sendThread.submit(() -> sendAlarm(alarmNode, 0));
                try {
                    future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    LOG.warn("processQueue: send timed out after {}ms for reduction key: {}", timeoutMs, alarmNode.alarm().getReductionKey());
                    eventForwarder.sendAlarmFailed(alarmNode.node().getId(), alarmNode.alarm().getReductionKey(), e.getMessage());
                } catch (ExecutionException e) {
                    LOG.error("processQueue: send failed for reduction key: {}", alarmNode.alarm().getReductionKey(), e.getCause());
                    eventForwarder.sendAlarmFailed(alarmNode.node().getId(), alarmNode.alarm().getReductionKey(), e.getMessage());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendAlarm(AlarmNode alarmNode, int retry) {
        Alarm alarm = alarmNode.alarm();
        if (maxRetry == retry) {
            LOG.warn("sendAlarm: skipping alarm with reduction key {}, too many retry {}", alarm.getReductionKey(), retry);
        }
        retry++;
        LOG.info("sendAlarm: processing alarm with reduction key: {}, retry: {}", alarm.getReductionKey(), retry);
        Alert alert = AlarmForwarder.toAlert(alarmNode.alarm(), alarmNode.parent());
        LOG.info("sendAlarm: converted to {}", alert);

        try {
            apiClientProvider.send(
                    alert,
                    ClientManager.asApiClientCredentials(connectionManager.getConnection().orElseThrow()));
            eventForwarder.sendAlarmSuccessful(alarm.getNode().getId(), alarm.getReductionKey());
            LOG.info("sendAlarm: forwarded: id={} asset={}, node={}, parent={}", alert.getId(), alert.getAsset(), alert.getNode(), alert.getParentalNodeLabel());
        } catch (ApiException e) {
            eventForwarder.sendAlarmFailed(alarm.getNode().getId(), alarm.getReductionKey(), e.getMessage());
            LOG.error("sendAlarm: failed to send: alarm {}, message: {}, body: {}",
                    alarm.getReductionKey(),
                    e.getMessage(),
                    e.getResponseBody(), e);
            try {
                Thread.sleep(retryDelay * retry);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            sendAlarm(alarmNode, retry);
        }
    }
}