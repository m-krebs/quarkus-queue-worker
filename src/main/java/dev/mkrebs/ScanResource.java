package dev.mkrebs;

import org.jboss.logging.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;

import dev.mkrebs.worker.TrivyWorker;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/scan/{image}")
public class ScanResource {
    private static final Logger LOGGER = Logger.getLogger(ScanResource.class);

    @Inject
    TrivyWorker trivyWorker;

    @Inject
    DockerClient dockerClient;

    @GET
    public Response scan(@PathParam("image") String image) {
        try {
            dockerClient.pullImageCmd("alpine").withTag("latesti").exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException ie) {
            LOGGER.error(ie);
        } catch (NotFoundException nfe) {
            LOGGER.error("Image alpine:latest not found.");
            return Response.status(Status.NOT_FOUND).build();
        }

        LOGGER.info("Received Scan REST request");
        trivyWorker.scanDocker(image);

        LOGGER.info("Return response");
        return Response.ok(image).build();
    }
}
