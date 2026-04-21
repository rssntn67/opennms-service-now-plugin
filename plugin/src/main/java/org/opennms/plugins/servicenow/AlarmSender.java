package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.model.Alarm;
import org.opennms.plugins.servicenow.client.ApiClientProvider;
import org.opennms.plugins.servicenow.client.ApiException;
import org.opennms.plugins.servicenow.client.ClientManager;
import org.opennms.plugins.servicenow.connection.ConnectionManager;
import org.opennms.plugins.servicenow.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AlarmSender {
    private static final Logger LOG = LoggerFactory.getLogger(AlarmSender.class);

    private final ConnectionManager connectionManager;
    private final ApiClientProvider apiClientProvider;
    private final EdgeService edgeService;
    private final PluginEventForwarder eventForwarder;
    private final int maxRetry;
    private final long retryDelay;

    private final LinkedBlockingQueue<Alarm> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = false;
    private ExecutorService senderThread;

    public AlarmSender(ConnectionManager connectionManager,
                       ApiClientProvider apiClientProvider,
                       EdgeService edgeService,
                       PluginEventForwarder eventForwarder,
                       int maxRetry,
                       long retryDelay) {
        this.connectionManager = connectionManager;
        this.apiClientProvider = apiClientProvider;
        this.edgeService = edgeService;
        this.eventForwarder = eventForwarder;
        this.maxRetry = maxRetry;
        this.retryDelay = retryDelay;
    }

    public void enqueue(Alarm alarm) {
        queue.offer(alarm);
    }

    public void start() {
        running = true;
        senderThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "alarm-forwarder-sender"));
        senderThread.submit(this::processQueue);
        LOG.info("start: alarm sender thread started");
    }

    public void stop() {
        running = false;
        if (senderThread != null) {
            senderThread.shutdownNow();
        }
        LOG.info("stop: alarm sender thread stopped");
    }

    private void processQueue() {
        while (running) {
            try {
                Alarm alarm = queue.poll(1, TimeUnit.SECONDS);
                if (alarm != null) {
                    sendAlarm(alarm, 0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendAlarm(Alarm alarm, int retry) {
        if (maxRetry == retry) {
            LOG.warn("sendAlarm: skipping alarm with reduction key {}, too many retry {}", alarm.getReductionKey(), retry);
        }
        retry++;
        LOG.info("sendAlarm: processing alarm with reduction key: {}, retry: {}", alarm.getReductionKey(), retry);
        Alert alert = AlarmForwarder.toAlert(alarm, edgeService.getParent(alarm.getNode()));
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
            sendAlarm(alarm, retry);
        }
    }
}