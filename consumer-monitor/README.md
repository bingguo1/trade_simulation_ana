## in consumer-monitor, how is the index.html get served to port 8080?
Here's the full chain:

index.html is served by Spring Boot's built-in static resource handler — no explicit controller needed.

The path matters: src/main/resources/static/index.html. Spring Boot (via spring-web) automatically serves any file under src/main/resources/static/ at the root of the HTTP server. So:

This is Spring Boot's auto-configured ResourceHttpRequestHandler — it's part of spring-boot-autoconfigure and activates automatically for any @SpringBootApplication. You don't need a @Controller or WebMvcConfigurer to enable it; the convention is just the static/ folder on the classpath.

The full serving chain:
```
Browser GET /
  │
  ▼
Embedded Tomcat (port 8080 via server.port in application.yml)
  │
  ▼
Spring MVC DispatcherServlet
  │  no @Controller matches "/"
  ▼
ResourceHttpRequestHandler (auto-configured)
  │  checks classpath:/static/
  ▼
index.html returned as text/html
```

After the page loads, the browser connects via SockJS/STOMP to ws://localhost:8080/ws (registered in WebSocketConfig) and subscribes to /topic/monitor, which MonitorController.broadcastSnapshot() pushes to every 500ms.

## what is "containerFactory"?why is it needed in KafkaListener?

The containerFactory attribute tells Spring which KafkaListenerContainerFactory bean to use to build the listener container for that specific @KafkaListener method.

Why it's needed here

Each @KafkaListener method receives a different Java type — TradeEvent vs QuoteEvent. Kafka messages are raw bytes; Spring needs to know how to deserialize those bytes into the right type before calling your method. That deserialization configuration lives inside the container factory bean.

## what is STOMP, why is it used? what is SimpMessagingTemplate?

STOMP is a simple text-based messaging protocol often used on top of WebSockets.

In this module, it is used because:

You have one server pushing updates to many browser clients.
You want topic-style pub/sub (/topic/monitor) instead of hand-rolling raw socket message routing.
Spring has first-class support for STOMP, including destinations, message conversion (Java object -> JSON), and broker abstraction.
How STOMP fits your flow:

1. Browser connects to /ws (SockJS/WebSocket).
1. Browser subscribes to /topic/monitor.
1. Server sends snapshots to /topic/monitor.
1. All subscribed clients receive the same payload

SimpMessagingTemplate is Spring’s helper class for sending messages to STOMP destinations from server-side Java code.