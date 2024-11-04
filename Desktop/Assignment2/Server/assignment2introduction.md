# Assignment2
## Github URL:https://github.com/XieWen2000/Distributed-System/blob/main/Desktop/cs6650
## Design 
The servlets run locally to generate 200k request while server, RabbitMQ and
consumer all run on different EC2 instances.
![ec2 instances](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%889.49.55.png)

### Client-Assignment 1
![Client](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%889.56.04.png)

### Server
![Server](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%889.59.08.png)
#### Overview
The SkierServlet is a Java servlet designed to handle both 
GET and POST requests for skier-related data in a RESTful 
manner. The servlet is responsible for processing incoming 
requests, validating their formats, and handling communication 
with RabbitMQ for message queueing. The servlet is built on
javax.servlet and leverages RabbitMQ as a message broker for 
handling requests asynchronously.
#### Major Components
1. Servlet Lifecycle Methods

`init(): `Establishes a connection to the RabbitMQ server when the servlet is initialized. This connection is reused for the servlet's lifetime.

`destroy():` Cleans up resources by closing the RabbitMQ connection when the servlet is destroyed.

2. Request Handlers

`doGet(): `Handles incoming GET requests for retrieving skier information. It validates the URL and responds with appropriate messages.

`doPost(): `Handles POST requests, validates incoming URL parameters and payload, and sends the information to a RabbitMQ queue for further processing.

3. RabbitMQ Integration

The servlet uses `RabbitMQ `to send messages asynchronously. The connection is managed using ConnectionFactory and each POST request is sent to a predefined queue (QUEUE_NAME).

4. URL Validation

The servlet parses the URL to ensure the request matches the expected pattern. This validation prevents incorrect requests from being processed.

## Consumer
![consumer](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%8810.05.18.png)
### Overview
The `MultiThreadedRabbitMQConsumer` is designed to efficiently handle and process messages from RabbitMQ in a concurrent and scalable manner. It leverages a thread pool to consume messages, allowing the system to dynamically scale based on the size of the message queue. The consumer utilizes a pooled object factory for RabbitMQ channels to maintain resource efficiency while ensuring high throughput.
#### Major Components
1. RabbitMQ Connection and Channel Pool

`Connection Factory:` A ConnectionFactory is used to create and manage connections to RabbitMQ. This connection is used to create channels that facilitate communication with RabbitMQ queues.

`Channel Pool: `A GenericObjectPool<Channel> is employed to manage a pool of reusable RabbitMQ channels. This ensures efficient usage of channels without the overhead of creating and closing channels repeatedly.

2. Thread Pool for Message Consumption

`Executor Service: `An ExecutorService is used to manage consumer threads. This allows multiple consumer tasks to run concurrently, enhancing throughput and ensuring efficient message processing.

`Dynamic Scaling:` A separate monitoring thread checks the size of the message queue and adjusts the number of consumer threads accordingly to handle high loads.

3. Message Processing and Load Monitoring

`Message Consumption:` The consumer tasks use RabbitMQ's DeliverCallback to process messages. The messages are parsed as JSON, and specific fields are extracted for further processing.

`Queue Monitoring:` A separate monitoring thread tracks the size of the message queue. If the queue grows beyond a certain threshold, additional consumer threads are added to handle the increased load.

After generating the .jar file and upload it to a separate EC2 instance and execute it there.
![RabbitMq consumer](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%887.04.13.png)

## Single Instance Tests
![截屏2024-11-03 下午8.26.00.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%888.26.00.png)
![截屏2024-11-03 下午7.22.56.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%887.22.56.png)
With one instance, message rate of RabbitMQ is around **500/s**,
![截屏2024-11-03 下午10.22.17.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%8810.22.17.png)

## Load Balanced Instance Tests
![截屏2024-11-03 下午10.28.02.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%8810.28.02.png)
With Load Balanced Instance, message rate of RabbitMQ is around **1800/s**,
![截屏2024-11-03 下午10.31.34.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%8810.31.34.png)
![截屏2024-11-03 下午10.35.10.png](pictures/%E6%88%AA%E5%B1%8F2024-11-03%20%E4%B8%8B%E5%8D%8810.35.10.png)