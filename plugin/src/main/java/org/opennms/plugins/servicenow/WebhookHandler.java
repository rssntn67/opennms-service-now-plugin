package org.opennms.plugins.servicenow;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("opennms-service-now")
public interface WebhookHandler {

    @GET
    @Path("/ping")
    Response ping();

}
