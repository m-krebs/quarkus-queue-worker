package dev.mkrebs;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.jboss.logging.Logger;

import com.github.dockerjava.api.DockerClient;
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
        LOGGER.info("Received GET REST request");
        String[] splitImage = image.split(":", 2);
        for (String s : splitImage) {
            System.out.println(s);
        }
        if (splitImage[1] == "") {
            splitImage[1] = "latest";
        }
        try {

            // dockerClient.pullImageCmd(splitImage[0]).withTag(splitImage[1]).exec(new
            // PullImageResultCallback())
            // .awaitCompletion();
            LOGGER.info("pulled alpine:latest");
            try (FileOutputStream outputStream = new FileOutputStream("image.tar")) {
                dockerClient.saveImageCmd(splitImage[0]).withTag(splitImage[1]).exec().transferTo(outputStream);
            } catch (FileNotFoundException e) {
                LOGGER.error(e);
            } catch (IOException e) {
                LOGGER.error("kek", e);
            } catch (NotFoundException e) {
                LOGGER.error("asdf");
            }
            // } catch (InterruptedException ie) {
            // LOGGER.error(ie);
        } catch (NotFoundException nfe) {
            LOGGER.error("Image alpine:latest not found.", nfe);
            return Response.status(Status.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            LOGGER.error(e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

        LOGGER.info("Received Scan REST request");
        trivyWorker.scanDocker(image);

        LOGGER.info("Return response");
        return Response.ok(image).build();
    }
}
