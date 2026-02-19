package dev.mkrebs;

import org.jboss.logging.Logger;

import dev.mkrebs.worker.TrivyWorker;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/scan/{image}")
public class ScanResource {
    private static final Logger LOGGER = Logger.getLogger(ScanResource.class);

    @Inject
    TrivyWorker trivyWorker;

    @GET
    public Response scan(@PathParam("image") String image) {
        LOGGER.info("Received Scan REST request");
        trivyWorker.scanDocker(image);

        LOGGER.info("Return response");
        return Response.ok(image).build();
    }
}
