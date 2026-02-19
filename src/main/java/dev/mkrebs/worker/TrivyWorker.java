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
        executor = vertx.createSharedWorkerExecutor("trivy-scanner", 4);
    }

    void tearDown(@Observes ShutdownEvent event) {
        executor.close();
    }

    public void scanDocker(String image) {
        LOGGER.info("Received Trivy Scan event");
        executor.executeBlocking(() -> {
            try {
                LOGGER.infof("Worker thread: %s", Thread.currentThread().getName());
                LOGGER.info(String.format("Scanning %s image", image));
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            LOGGER.info("End long running trivy image scanning event");
            return "something";
        }, false)
                .subscribe().with(
                        item -> {
                            System.out.println(item);
                        },
                        failure -> {
                            System.out.println(failure);
                        });
    }
}
