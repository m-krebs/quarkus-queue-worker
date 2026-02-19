package dev.mkrebs.worker;

import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.vertx.mutiny.core.WorkerExecutor;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class TrivyWorker {
    private static final Logger LOGGER = Logger.getLogger(TrivyWorker.class);
    private final WorkerExecutor executor;

    TrivyWorker(Vertx vertx) {
        executor = vertx.createSharedWorkerExecutor("trivy-scanner");
    }

    void tearDown(@Observes ShutdownEvent event) {
        executor.close();
    }

    public void scanDocker(String image) {
        LOGGER.info("Received Trivy Scan event");
        try {
            LOGGER.info(String.format("Scanning %s image", image));
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.info("End long running trivy image scanning event");
    }
}
