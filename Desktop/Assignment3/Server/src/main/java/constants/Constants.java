package constants;

public class Constants {

    public static final String QUEUE_NAME = "skiersQueue";
    public static final String RABBITMQ_HOST = "54.236.239.74";
    public static final String BASE_PATH = "http://servletBanance-1729867918.us-east-1.elb.amazonaws.com:8080/Server-1.0-SNAPSHOT";
    public static final int TOTAL_REQUESTS = 200000;
    public static final int INITIAL_THREADS = 32;
    public static final int EXTRA_THREADS = 672;
    public static final int INITIAL_REQUESTS_PER_THREAD = 1000;
    public static final int MAX_RETRIES = 5;

}
