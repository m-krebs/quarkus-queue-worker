# Queue Worker Implementation Guide

A step-by-step guide to add in-memory queue functionality using Vert.x EventBus.

## Overview

- **Approach**: Vert.x EventBus (already included via `quarkus-rest`)
- **No additional dependencies required**
- **Pattern**: REST endpoint publishes jobs → EventBus → Worker consumes and processes

---

## Step 1: Create the ScanJob Message Class

Create `src/main/java/dev/mkrebs/worker/ScanJob.java`:

```java
package dev.mkrebs.worker;

public class ScanJob {
    private final String image;
    private final String requestId;
    private final long timestamp;

    public ScanJob(String image, String requestId) {
        this.image = image;
        this.requestId = requestId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getImage() {
        return image;
    }

    public String getRequestId() {
        return requestId;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
```

---

## Step 2: Create the EventBus Codec

Create `src/main/java/dev/mkrebs/worker/ScanJobCodec.java`:

```java
package dev.mkrebs.worker;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;

public class ScanJobCodec implements MessageCodec<ScanJob, ScanJob> {

    @Override
    public void encodeToWire(Buffer buffer, ScanJob job) {
        // Not needed for local-only EventBus
    }

    @Override
    public ScanJob decodeFromWire(int pos, Buffer buffer) {
        // Not needed for local-only EventBus
        return null;
    }

    @Override
    public ScanJob transform(ScanJob job) {
        // Pass-through for local messages
        return job;
    }

    @Override
    public String name() {
        return "scan-job-codec";
    }

    @Override
    public byte systemCodecID() {
        return -1; // Custom codec
    }
}
```

---

## Step 3: Modify ScanResource to Publish Jobs

Update `src/main/java/dev/mkrebs/ScanResource.java`:

```java
package dev.mkrebs;

import java.util.UUID;

import org.jboss.logging.Logger;

import dev.mkrebs.worker.ScanJob;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/scan/{image}")
public class ScanResource {
    private static final Logger LOGGER = Logger.getLogger(ScanResource.class);
    private static final String SCAN_JOBS_ADDRESS = "scan-jobs";

    @Inject
    EventBus eventBus;

    @GET
    public Response scan(@PathParam("image") String image) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.infof("Received scan request [%s] for image: %s", requestId, image);

        // Publish to EventBus (fire-and-forget)
        ScanJob job = new ScanJob(image, requestId);
        eventBus.send(SCAN_JOBS_ADDRESS, job);

        LOGGER.infof("Job [%s] queued successfully", requestId);
        return Response.accepted()
                .entity(new ScanResponse(requestId, "Job queued"))
                .build();
    }

    public record ScanResponse(String requestId, String status) {}
}
```

---

## Step 4: Modify TrivyWorker to Consume Jobs

Update `src/main/java/dev/mkrebs/worker/TrivyWorker.java`:

```java
package dev.mkrebs.worker;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.WorkerExecutor;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.core.eventbus.MessageConsumer;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

@Singleton
public class TrivyWorker {
    private static final Logger LOGGER = Logger.getLogger(TrivyWorker.class);
    private static final String SCAN_JOBS_ADDRESS = "scan-jobs";

    private final Vertx vertx;
    private final EventBus eventBus;
    private final int poolSize;

    private WorkerExecutor executor;
    private MessageConsumer<ScanJob> consumer;

    TrivyWorker(Vertx vertx, EventBus eventBus,
                @ConfigProperty(name = "scan.worker.pool-size", defaultValue = "5") int poolSize) {
        this.vertx = vertx;
        this.eventBus = eventBus;
        this.poolSize = poolSize;
    }

    void onStart(@Observes StartupEvent event) {
        LOGGER.info("Starting TrivyWorker...");

        // Register custom codec for ScanJob
        vertx.getDelegate().eventBus()
            .registerDefaultCodec(ScanJob.class, new ScanJobCodec());

        // Create worker executor pool
        executor = vertx.createSharedWorkerExecutor("trivy-scanner", poolSize);

        // Register EventBus consumer
        consumer = eventBus.consumer(SCAN_JOBS_ADDRESS);
        consumer.handler(message -> {
            ScanJob job = message.body();
            LOGGER.infof("Received job [%s] from queue", job.getRequestId());

            // Execute on worker thread pool (non-blocking)
            executor.executeBlocking(() -> {
                processScan(job);
                return null;
            }).subscribe().with(
                success -> LOGGER.infof("Job [%s] completed", job.getRequestId()),
                failure -> LOGGER.errorf(failure, "Job [%s] failed", job.getRequestId())
            );
        });

        LOGGER.infof("TrivyWorker started with pool size: %d", poolSize);
    }

    void onShutdown(@Observes ShutdownEvent event) {
        LOGGER.info("Shutting down TrivyWorker...");
        if (consumer != null) {
            consumer.unregisterAndAwait();
        }
        if (executor != null) {
            executor.close();
        }
    }

    private void processScan(ScanJob job) {
        LOGGER.infof("Processing scan for image: %s [%s]", job.getImage(), job.getRequestId());
        try {
            // TODO: Replace with actual Trivy scan logic
            Thread.sleep(5000L);
            LOGGER.infof("Scan completed for image: %s [%s]", job.getImage(), job.getRequestId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.errorf(e, "Scan interrupted for job [%s]", job.getRequestId());
        }
    }
}
```

---

## Step 5: Add Configuration

Update `src/main/resources/application.properties`:

```properties
# Worker pool configuration
scan.worker.pool-size=5
```

---

## Verification

1. **Start the dev server:**
   ```bash
   just dev
   ```

2. **Send a scan request:**
   ```bash
   curl http://localhost:8080/scan/nginx:latest
   ```

3. **Expected response:**
   ```json
   {"requestId":"<uuid>","status":"Job queued"}
   ```
   HTTP Status: `202 Accepted`

4. **Check logs for:**
   - `Received scan request [<id>] for image: nginx:latest`
   - `Job [<id>] queued successfully`
   - `Received job [<id>] from queue`
   - `Processing scan for image: nginx:latest [<id>]`
   - `Scan completed for image: nginx:latest [<id>]`

5. **Run tests:**
   ```bash
   just ci-test
   ```

---

## Optional: Add Backpressure Control

To limit the queue size and reject requests when full, create `ScanJobQueue.java`:

```java
package dev.mkrebs.worker;

import java.util.concurrent.Semaphore;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScanJobQueue {
    private static final Logger LOGGER = Logger.getLogger(ScanJobQueue.class);
    private static final String SCAN_JOBS_ADDRESS = "scan-jobs";

    private final EventBus eventBus;
    private final Semaphore permits;

    ScanJobQueue(EventBus eventBus,
                 @ConfigProperty(name = "scan.worker.max-queue-size", defaultValue = "100") int maxQueueSize) {
        this.eventBus = eventBus;
        this.permits = new Semaphore(maxQueueSize);
    }

    public boolean submit(ScanJob job) {
        if (!permits.tryAcquire()) {
            LOGGER.warnf("Queue full, rejecting job [%s]", job.getRequestId());
            return false;
        }
        eventBus.send(SCAN_JOBS_ADDRESS, job);
        return true;
    }

    public void releasePermit() {
        permits.release();
    }
}
```

Then inject `ScanJobQueue` in `ScanResource` instead of `EventBus`, and call `releasePermit()` in `TrivyWorker` after processing.
