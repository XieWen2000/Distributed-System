import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.json.JSONObject;

import static constants.Constants.QUEUE_NAME;
import static constants.Constants.RABBITMQ_HOST;

@WebServlet(value = "/skiers/*")
public class SkierServlet extends HttpServlet {
    private Connection connection;
    private ConnectionFactory factory;

    @Override
    public void init() throws ServletException {
        super.init();
        try {
            factory = new ConnectionFactory();
            factory.setHost(RABBITMQ_HOST);
            factory.setPort(5672);
            factory.setUsername("username");
            factory.setPassword("password");
            factory.setConnectionTimeout(60000);
            factory.setHandshakeTimeout(60000);
            connection = factory.newConnection();
        } catch (Exception e) {
            throw new ServletException("Failed to initialize RabbitMQ connection", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/plain");
        String urlPath = req.getPathInfo();

        // check we have a URL!
        if (urlPath == null || urlPath.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("Please provide more path parameters, e.g., /skiers/{resortID}/seasons/{seasonID}/days/{dayID}/skiers/{skierID}");
            return;
        }

        String[] urlParts = urlPath.split("/");
        // Validate the URL path
        if (!isUrlValid(urlParts)) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.getWriter().write("Invalid URL path");
        } else {
            res.setStatus(HttpServletResponse.SC_OK);
            // do any sophisticated processing with urlParts which contains all the url params
            // TODO: process url params in `urlParts`
            res.getWriter().write("Valid path: " + urlPath);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        String urlPath = req.getPathInfo();

        String[] urlParts = urlPath.split("/");

        if (urlParts.length > 0 && urlParts[0].isEmpty()) {
            urlParts[0] = "skiers";
        }
        for (int i = 0; i < urlParts.length; i++) {
            urlParts[i] = urlParts[i].trim();
        }
        if (!isUrlValid(urlParts)) {
            res.getWriter().write("{\"error\": \"Invalid URL format\"}");

            return;
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }


        JSONObject jsonObject = new JSONObject(sb.toString());

        jsonObject.put("resortID", Integer.valueOf(urlParts[1]))
                .put("seasonID", urlParts[3])
                .put("dayID", urlParts[5])
                .put("skierID", Integer.valueOf(urlParts[7]));

        try (Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            channel.basicPublish("", QUEUE_NAME, null, jsonObject.toString().getBytes());
            res.setStatus(HttpServletResponse.SC_OK);
            res.getWriter().write("{\"message\": \"Payload sent successfully to RabbitMQ\"}");
            System.out.println(" [x] Sent '" + jsonObject + "'");
        } catch (Exception e) {
            e.printStackTrace();
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.getWriter().write("{\"error\": \"Failed to send payload to RabbitMQ\"}");
        }
    }

    private boolean isUrlValid(String[] urlPath) {
        if (urlPath.length == 8) {
            return "seasons".equals(urlPath[2]) && "days".equals(urlPath[4]) && "skiers".equals(urlPath[6]) &&
                    isNumeric(urlPath[1]) && isNumeric(urlPath[3]) && isNumeric(urlPath[5]) && isNumeric(urlPath[7]);
        }
        return false;
    }

    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
