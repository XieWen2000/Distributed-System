import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import factory.ChannelFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static constants.Constants.*;

public class MultiThreadedRabbitMQConsumer {

    private static final Map<String, Integer> skierLiftRides = new ConcurrentHashMap<>();

    private static GenericObjectPool<Channel> channelPool;

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(5672);
        factory.setUsername("username");
        factory.setPassword("password");
        factory.setConnectionTimeout(60000);
        factory.setHandshakeTimeout(60000);

        Connection connection = factory.newConnection();

        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(20);
        config.setMinIdle(5);
        channelPool = new GenericObjectPool<>(new ChannelFactory(connection), config);

        ExecutorService executorService = Executors.newFixedThreadPool(INITIAL_THREAD_POOL_SIZE);

        for (int i = 0; i < INITIAL_THREAD_POOL_SIZE; i++) {
            createConsumerTask(executorService);
        }

        new Thread(() -> {
            try {
                Channel monitoringChannel = connection.createChannel();
                while (true) {
                    int queueMessageCount = monitoringChannel.queueDeclarePassive(QUEUE_NAME).getMessageCount();
                    int currentPoolSize = ((java.util.concurrent.ThreadPoolExecutor) executorService).getPoolSize();

                    if (queueMessageCount > 1000 && currentPoolSize < MAX_THREAD_POOL_SIZE) {
                        System.out.println("Increasing consumer threads due to high queue size: " + queueMessageCount);
                        createConsumerTask(executorService);
                    }

                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void createConsumerTask(ExecutorService executorService) {
        executorService.submit(() -> {
            Channel channel = null;
            try {
                channel = channelPool.borrowObject();
                channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");
                    System.out.println(" [x] Received '" + message + "'");
                    processMessage(message);
                };

                channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> {
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (channel != null) {
                    try {
                        channelPool.returnObject(channel);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    // Process the message and update the lift rides for each skier
    private static void processMessage(String message) {
        try {
            // Parse the message as a JSON object
            JSONObject jsonObject = new JSONObject(message);

            // Extract the fields you need
            Integer skierID = jsonObject.getInt("skierID");
            int liftID = jsonObject.getInt("liftID");
            System.out.println("Processed message: skierID=" + skierID + ", liftID=" + liftID);

            // Update the skier lift ride count in the ConcurrentHashMap
            // This will increment the count for each skierID
            skierLiftRides.merge(skierID.toString(), 1, Integer::sum);
        } catch (Exception e) {
            System.err.println("Invalid message format: " + message);
        }
    }

}



