package client.part1;

import client.LiftRideEvent;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SkierClient {
    private static final String BASE_PATH = "http://18.208.248.198:8080/JavaServlets_war/skiers";
    private static final int TOTAL_REQUESTS = 200000;
    private static final int INITIAL_THREADS = 32;
    private static final int EXTRA_THREADS = 672;
    private static final int INITIAL_REQUESTS_PER_THREAD = 1000;
    private static final int MAX_RETRIES = 5;

    private AtomicInteger successfulRequests = new AtomicInteger(0);
    private AtomicInteger failedRequests = new AtomicInteger(0);
    private long startTime;

    private BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>();

    public static void main(String[] args) {
        SkierClient client = new SkierClient();
        client.start();
    }

    public void start() {
        System.out.println("Starting SkierClient...");
        System.out.println("Initail threads: "+ INITIAL_THREADS);
        // Start generating lift ride events in a separate thread
        new Thread(new EventGenerator(TOTAL_REQUESTS, eventQueue)).start();

        // Track the start time
        startTime = System.currentTimeMillis();

        // Create an executor for initial threads
        ExecutorService initialExecutor = Executors.newFixedThreadPool(INITIAL_THREADS);
        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREADS);

        for (int i = 0; i < INITIAL_THREADS; i++) {
            initialExecutor.execute(new InitialRequestSender(INITIAL_REQUESTS_PER_THREAD, eventQueue, successfulRequests, failedRequests, initialLatch));
        }

        try {
            // Wait for all initial threads to finish
            initialLatch.await();
            System.out.println("Extra threads: "+ EXTRA_THREADS);

            // Calculate remaining requests after initial threads
            int remainingRequests = TOTAL_REQUESTS - (INITIAL_THREADS * INITIAL_REQUESTS_PER_THREAD);

            // Create additional threads to complete remaining requests
            int requestsPerThread = Math.max(remainingRequests / EXTRA_THREADS, 1);
            int remainingRequestsAfterBatch = remainingRequests % EXTRA_THREADS;

            ExecutorService extraExecutor = Executors.newFixedThreadPool(EXTRA_THREADS);
            CountDownLatch extraLatch = new CountDownLatch(EXTRA_THREADS);

            for (int i = 0; i < EXTRA_THREADS; i++) {
                int requestsToSend = (i < remainingRequestsAfterBatch) ? requestsPerThread + 1 : requestsPerThread;
                extraExecutor.execute(new RequestSender(requestsToSend, eventQueue, successfulRequests, failedRequests, extraLatch));
            }

            // Wait for the additional threads to complete
            extraLatch.await();

            extraExecutor.shutdown();
            extraExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Calculate and print statistics
        long endTime = System.currentTimeMillis();
        printStatistics(endTime - startTime);

        System.out.println("Executor shutdown.");
        initialExecutor.shutdown();
    }

    private void printStatistics(long totalTime) {
        System.out.println("Total successful requests: " + successfulRequests.get());
        System.out.println("Total failed requests: " + failedRequests.get());
        System.out.println("Total runtime (ms): " + totalTime);
        System.out.println("Throughput (requests/sec): " + (TOTAL_REQUESTS / (totalTime / 1000.0)));
    }

    // Inner class for generating lift ride events
    private static class EventGenerator implements Runnable {
        private final int totalRequests;
        private final BlockingQueue<LiftRideEvent> queue;

        public EventGenerator(int totalRequests, BlockingQueue<LiftRideEvent> queue) {
            this.totalRequests = totalRequests;
            this.queue = queue;
        }

        @Override
        public void run() {
            for (int i = 0; i < totalRequests; i++) {
                LiftRideEvent event = LiftRideEvent.generateRandomEvent();
                try {
                    queue.put(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Event generation completed, Event queue size: " + queue.size());
        }
    }

    // Inner class for sending requests initially
    private static class InitialRequestSender implements Runnable {
        private final int requestsToSend;
        private final BlockingQueue<LiftRideEvent> queue;
        private final AtomicInteger successCount;
        private final AtomicInteger failCount;
        private final CountDownLatch latch;

        public InitialRequestSender(int requestsToSend, BlockingQueue<LiftRideEvent> queue, AtomicInteger successCount, AtomicInteger failCount, CountDownLatch latch) {
            this.requestsToSend = requestsToSend;
            this.queue = queue;
            this.successCount = successCount;
            this.failCount = failCount;
            this.latch = latch;
        }

        @Override
        public void run() {
            sendRequests(requestsToSend);
            latch.countDown(); // Notify that this thread is finished
        }

        private void sendRequests(int requestsToSend) {
            ApiClient client = new ApiClient();
            client.setBasePath(BASE_PATH);
            SkiersApi apiInstance = new SkiersApi(client);

            for (int i = 0; i < requestsToSend; i++) {
                try {
                    LiftRideEvent event = queue.poll(1, TimeUnit.SECONDS);
                    if (event != null) {
                        sendWithRetries(apiInstance, event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void sendWithRetries(SkiersApi apiInstance, LiftRideEvent event) {
            int attempt = 0;
            boolean success = false;

            while (attempt < MAX_RETRIES && !success) {
                try {
                    LiftRide liftRide = convertToLiftRide(event);
                    apiInstance.writeNewLiftRide(liftRide, event.getResortID(),
                            String.valueOf(event.getSeasonID()), String.valueOf(event.getDayID()), event.getSkierID());

                    successCount.incrementAndGet();
                    success = true;
                } catch (ApiException | RuntimeException e) {
                    attempt++;
                    if (attempt == MAX_RETRIES) {
                        failCount.incrementAndGet();
                    }
                }
            }
        }

        private LiftRide convertToLiftRide(LiftRideEvent event) {
            LiftRide liftRide = new LiftRide();
            liftRide.setLiftID(event.getLiftID());
            liftRide.setTime(event.getTime());
            return liftRide;
        }
    }

    // Inner class for sending remaining requests
    private static class RequestSender implements Runnable {
        private final int requestsToSend;
        private final BlockingQueue<LiftRideEvent> queue;
        private final AtomicInteger successCount;
        private final AtomicInteger failCount;
        private final CountDownLatch latch;

        public RequestSender(int requestsToSend, BlockingQueue<LiftRideEvent> queue, AtomicInteger successCount, AtomicInteger failCount, CountDownLatch latch) {
            this.requestsToSend = requestsToSend;
            this.queue = queue;
            this.successCount = successCount;
            this.failCount = failCount;
            this.latch = latch;
        }

        @Override
        public void run() {
            new InitialRequestSender(requestsToSend, queue, successCount, failCount, latch).sendRequests(requestsToSend);
            latch.countDown();
        }
    }
}
