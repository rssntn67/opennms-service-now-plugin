package org.opennms.plugins.servicenow;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookHandlerImpl implements WebhookHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WebhookHandlerImpl.class);

    @Override
    public Response ping() {
        return Response.ok("pong").build();
    }

}

