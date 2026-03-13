package org.opennms.plugins.servicenow;

import org.opennms.integration.api.v1.events.EventForwarder;
import org.opennms.integration.api.v1.model.immutables.ImmutableEventParameter;
import org.opennms.integration.api.v1.model.immutables.ImmutableInMemoryEvent;

import java.util.Objects;

public class PluginEventForwarder {

    private static final String SOURCE = "opennms-service-now-plugin";
    private static final String UEI_PREFIX = "uei.opennms.org/opennms-service-nowPlugin";
    public static final String SEND_EVENT_FAILED_UEI = UEI_PREFIX + "/sendEventFailed";
    public static final String SEND_EVENT_SUCCESSFUL_UEI = UEI_PREFIX + "/sendEventSuccessful";
    public static final String SEND_ASSET_FAILED_UEI = UEI_PREFIX + "/sendAssetFailed";
    public static final String SEND_ASSET_SUCCESSFUL_UEI = UEI_PREFIX + "/sendAssetSuccessful";

    private final EventForwarder eventForwarder;

    public PluginEventForwarder(EventForwarder eventForwarder) {
        this.eventForwarder = Objects.requireNonNull(eventForwarder);
    }

    public void sendAlarmSuccessful(int nodeId, String reductionKey) {
        eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                .setUei(SEND_EVENT_SUCCESSFUL_UEI)
                .setNodeId(nodeId)
                .setSource(SOURCE)
                .addParameter(ImmutableEventParameter.newBuilder()
                        .setName("reductionKey")
                        .setValue(reductionKey)
                        .build())
                .build());
    }

    public void sendAlarmFailed(int nodeId, String reductionKey, String message) {
        eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                .setUei(SEND_EVENT_FAILED_UEI)
                .setNodeId(nodeId)
                .setSource(SOURCE)
                .addParameter(ImmutableEventParameter.newBuilder()
                        .setName("reductionKey")
                        .setValue(reductionKey)
                        .build())
                .addParameter(ImmutableEventParameter.newBuilder()
                        .setName("message")
                        .setValue(message)
                        .build())
                .build());
    }

    public void sendAssetSuccessful(int nodeId) {
        eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                .setUei(SEND_ASSET_SUCCESSFUL_UEI)
                .setNodeId(nodeId)
                .setSource(SOURCE)
                .build());
    }

    public void sendAssetFailed(int nodeId, String message) {
        eventForwarder.sendAsync(ImmutableInMemoryEvent.newBuilder()
                .setUei(SEND_ASSET_FAILED_UEI)
                .setNodeId(nodeId)
                .setSource(SOURCE)
                .addParameter(ImmutableEventParameter.newBuilder()
                        .setName("message")
                        .setValue(message)
                        .build())
                .build());
    }
}