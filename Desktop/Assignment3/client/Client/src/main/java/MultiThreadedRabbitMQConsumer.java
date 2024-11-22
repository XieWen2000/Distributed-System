import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import factory.ChannelFactory;
import java.util.Map;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.bson.Document;
import org.json.JSONObject;

public class MultiThreadedRabbitMQConsumer {
    private static GenericObjectPool<Channel> channelPool;
    //private static final String CONNECTION_STRING = "mongodb+srv://myUser:myPassword@cluster0.mceia.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0";
    private static final MongoClient mongoClient = MongoClients.create("mongodb+srv://myUser:myPassword@cluster0.mceia.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0");
    private static final MongoDatabase database;
    private static final MongoCollection<Document> collection;

    public MultiThreadedRabbitMQConsumer() {
    }

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("54.236.239.74");
        factory.setPort(5672);
        factory.setUsername("username");
        factory.setPassword("password");
        factory.setConnectionTimeout(60000);
        factory.setHandshakeTimeout(60000);
        Connection connection = factory.newConnection();
        GenericObjectPoolConfig<Channel> config = new GenericObjectPoolConfig();
        config.setMaxTotal(20);
        config.setMinIdle(5);
        channelPool = new GenericObjectPool(new ChannelFactory(connection), config);
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        for(int i = 0; i < 10; ++i) {
            createConsumerTask(executorService);
        }

        (new Thread(() -> {
            try {
                Channel monitoringChannel = connection.createChannel();

                while(true) {
                    int queueMessageCount = monitoringChannel.queueDeclarePassive("skiersQueue").getMessageCount();
                    int currentPoolSize = ((ThreadPoolExecutor)executorService).getPoolSize();
                    if (queueMessageCount > 1000 && currentPoolSize < 50) {
                        System.out.println("Increasing consumer threads due to high queue size: " + queueMessageCount);
                        createConsumerTask(executorService);
                    }

                    Thread.sleep(5000L);
                }
            } catch (Exception var5) {
                Exception e = var5;
                e.printStackTrace();
            }
        })).start();
    }

    private static void createConsumerTask(ExecutorService executorService) {
        executorService.submit(() -> {
            Channel channel = null;

            try {
                channel = (Channel)channelPool.borrowObject();
                channel.queueDeclare("skiersQueue", true, false, false, (Map)null);
                System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");
                    System.out.println(" [x] Received '" + message + "'");
                    processMessage(message);
                };
                channel.basicConsume("skiersQueue", true, deliverCallback, (consumerTag) -> {
                });
            } catch (Exception var10) {
                Exception e = var10;
                e.printStackTrace();
            } finally {
                if (channel != null) {
                    try {
                        channelPool.returnObject(channel);
                    } catch (Exception var9) {
                        Exception ex = var9;
                        ex.printStackTrace();
                    }
                }

            }

        });
    }

    private static void processMessage(String message) {
        try {
            JSONObject jsonObject = new JSONObject(message);
            Integer skierID = jsonObject.getInt("skierID");
            Integer season = jsonObject.getInt("seasonID");
            Integer liftID = jsonObject.getInt("liftID");
            Integer resortID = jsonObject.getInt("resortID");
            Integer time = jsonObject.getInt("time");
            Integer day = jsonObject.getInt("dayID");

            // Build skier activity document
            Document skierActivity = new Document("resortId", resortID)
                    .append("checkIn", time)
                    .append("day", day)
                    .append("lifts", new Document("liftId", liftID).append("time", time));

            // Update Skiers collection
            collection.updateOne(
                    new Document("skierId", skierID).append("season", season),
                    new Document("$push", new Document("activities", skierActivity)),
                    new UpdateOptions().upsert(true)
            );

            // Update ResortActivity collection
            MongoCollection<Document> resortCollection = database.getCollection("ResortActivity");
            resortCollection.updateOne(
                    new Document("resortId", resortID).append("checkIn", time), // Group by check-in date
                    new Document("$addToSet", new Document("uniqueSkiers", skierID)),
                    new UpdateOptions().upsert(true)
            );

            System.out.println("Message processed and written to MongoDB.");
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }


    static {
        database = mongoClient.getDatabase("SkierDB");
        collection = database.getCollection("SkierData");

}}
