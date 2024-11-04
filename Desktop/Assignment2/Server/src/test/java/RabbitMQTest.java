import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQTest {
    public static void main(String[] args) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("98.83.133.122");
        factory.setPort(5672);
        factory.setUsername("username");
        factory.setPassword("password");
        factory.setConnectionTimeout(60000); // 60 秒
        factory.setHandshakeTimeout(60000); // 60 秒

        try (Connection connection = factory.newConnection()) {
            System.out.println("Connected to RabbitMQ successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

