
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.SkiersApi;
import io.swagger.client.model.LiftRide;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static constants.Constants.*;

public class SkierClient {

    private AtomicInteger successfulRequests = new AtomicInteger(0);
    private AtomicInteger failedRequests = new AtomicInteger(0);
    private long startTime;

    private BlockingQueue<LiftRideEvent> eventQueue = new LinkedBlockingQueue<>();
    private List<LatencyRecord> latencyRecords = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        SkierClient client = new SkierClient();
        client.start();
    }

    public void start() {
        System.out.println("Starting SkierClient...");
        System.out.println("Initail threads: "+ INITIAL_THREADS);
        new Thread(new EventGenerator(TOTAL_REQUESTS, eventQueue)).start();
        startTime = System.currentTimeMillis();

        // Create an executor for initial threads
        ExecutorService initialExecutor = Executors.newFixedThreadPool(INITIAL_THREADS);
        CountDownLatch initialLatch = new CountDownLatch(INITIAL_THREADS);

        // Create and execute initial threads
        System.out.println("Starting first stage.");
        System.out.println("Initail threads: "+ INITIAL_THREADS);
        for (int i = 0; i < INITIAL_THREADS; i++) {
            initialExecutor.execute(new RequestSender(INITIAL_REQUESTS_PER_THREAD, eventQueue, successfulRequests, failedRequests, initialLatch));
        }

        try {
            // Wait for all initial threads to finish
            initialLatch.await();
            System.out.println("Starting second stage.");
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

        List<Long> latencies = new ArrayList<>();
        for (LatencyRecord record : latencyRecords) {
            latencies.add(record.getLatency());
        }
        Collections.sort(latencies);

        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long median = latencies.get(latencies.size() / 2);
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);

        System.out.println("Mean response time (ms): " + mean);
        System.out.println("Median response time (ms): " + median);
        System.out.println("P99 response time (ms): " + p99);
        System.out.println("Min response time (ms): " + min);
        System.out.println("Max response time (ms): " + max);

        // Write latency records to CSV file
        try (FileWriter writer = new FileWriter("latency_records.csv")) {
            writer.write("start_time,request_type,latency\n");
            for (LatencyRecord record : latencyRecords) {
                writer.write(record.toCsv() + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    // Unified RequestSender class
    private class RequestSender implements Runnable {
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
                long start = System.currentTimeMillis();
                try {
                    LiftRide liftRide = convertToLiftRide(event);
                    apiInstance.writeNewLiftRide(liftRide, event.getResortID(),
                            String.valueOf(event.getSeasonID()), String.valueOf(event.getDayID()), event.getSkierID());
                    long end = System.currentTimeMillis();

                    successCount.incrementAndGet();
                    success = true;
                    long latency = end - start;
                    latencyRecords.add(new LatencyRecord(start, "POST", latency));
                    System.out.println("Request sent successfully: " + liftRide);
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

    // Class to hold latency records
    private static class LatencyRecord {
        private final long startTime;
        private final String requestType;
        private final long latency;

        public LatencyRecord(long startTime, String requestType, long latency) {
            this.startTime = startTime;
            this.requestType = requestType;
            this.latency = latency;
        }

        public long getLatency() {
            return latency;
        }

        public String toCsv() {
            return startTime + "," + requestType + "," + latency;
        }
    }
}
