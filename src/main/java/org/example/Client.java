package org.example;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Client implements Runnable {

    private static final int DEFAULT_STOP_STREAM_TIME = 20;
    private String username;
    private String password;
    private Integer streamEndTime;


    private String url;
    private Map<String, String> headerParams;
    private EventHandler eventHandler;

    private InputStream inputStream;
    private AtomicBoolean shouldRun = new AtomicBoolean(true);

    private long lastReceivedMessageTime = 0L;




    @Builder
    public Client(String url, Map<String, String> headerParams, EventHandler eventHandler, String username,
                  String password, Integer streamEndTime) {
        super();
        if (url == null) {
            throw new IllegalArgumentException("url cannot be null");
        }
        if (eventHandler == null) {
            throw new IllegalArgumentException("eventHandler cannot be null");
        }
        if (username == null) {
            throw new IllegalArgumentException("username cannot be null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password cannot be null");
        }
        if (streamEndTime == null) {
            streamEndTime = DEFAULT_STOP_STREAM_TIME;
        }

        this.url = url;
        this.username = username;
        this.password = password;
        this.streamEndTime = streamEndTime;

        this.headerParams = headerParams;
        if (this.headerParams == null) {
            this.headerParams = new HashMap<>();
        }

        this.eventHandler = eventHandler;


    }


    private void getChanges() {
        while (shouldRun.get() && !Thread.currentThread().isInterrupted()) {
            try {
                getChangesHelper();
            } catch (Exception e) {
                String errorMessage = "Got error: " + e.getMessage();
                log.error(errorMessage, e);
            }


        }
    }


    public void getChangesHelper() throws IOException {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .version(HttpClient.Version.HTTP_2)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .header("Authorization", getBasicAuthenticationHeader(this.username, this.password));

            Set<Map.Entry<String, String>> customHeaderParams = headerParams.entrySet();
            for (Map.Entry<String, String> headerParam : customHeaderParams) {
                requestBuilder.header(headerParam.getKey(), headerParam.getValue());
            }
            HttpRequest request = requestBuilder.build();

            HttpResponse<InputStream> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == HttpStatus.SC_OK) {
                inputStream = response.body();
                handleResponse(inputStream);
            } else {
                String body = getNonStreamBodyResponse(response);
                log.error("Got error response: {} {}", response.statusCode(), body);
            }
        } catch (Exception e) {
            String errorMessage = "Got exception: " + e.getMessage() + ", cause: " + e.getCause() + ", class: " + e.getClass();
            if (e.getCause() instanceof InterruptedException) {
                log.debug(errorMessage);
            } else {
                log.error(errorMessage, e);
                throw new IOException(errorMessage, e);
            }
        }
    }

    private static final String getBasicAuthenticationHeader(String username, String password) {
        String valueToEncode = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }

    public String getNonStreamBodyResponse(HttpResponse<InputStream> response) throws IOException {
        try (InputStream currentInputStream = response.body()) {
            return IOUtils.toString(response.body(), StandardCharsets.UTF_8);
        }
    }

    private void handleResponse(InputStream inputStream) throws IOException {
        log.info("Handling response.");
        try (BufferedInputStream in = IOUtils.buffer(inputStream)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = null;
                StringBuilder messageBuilder = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    lastReceivedMessageTime = System.currentTimeMillis();
                    String content = line;
                    if (content.startsWith("data:")) {
                        log.info(content);
                        messageBuilder.append(content.substring(5));
                    } else {
                        log.debug("Got non-data line: {}", content);
                    }
                    if (line.trim().isEmpty() && messageBuilder.length() > 0) {
                        String message = messageBuilder.toString();
                        handleData(message);
                        messageBuilder = new StringBuilder();
                    }
                }
                if (messageBuilder.length() > 0) {
                    log.info("Non-processed data: {}", messageBuilder);
                }
            }
        } catch (Exception e) {
            String errorMessage = "Could not handle response: " + e.getMessage();
            if ("closed".equals(e.getMessage())) {
                log.info(errorMessage);
            } else {
                log.error(errorMessage);
                throw new IOException(errorMessage, e);
            }
        }
        log.info("done handling response.");
    }

    private void handleData(String eventText) {
        try {
            eventHandler.handle(eventText.trim());
        } catch (Exception e) {
            log.error("Error handleData: " + e.getMessage(), e);
        }
    }


    public void stopCurrentRequest() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception e) {
                log.error("Got error stopCurrentRequest: " + e.getMessage(), e);
            }
        }
    }
//
//        public void start() {
////        CompletableFuture.runAsync( pool.execute(() -> getChanges()))
////                .orTimeout(this.streamEndTime, TimeUnit.SECONDS)
////                .exceptionally(throwable -> {
////                    log.error("An error occurred", throwable);
////                    return null;
////                });
//            pool.execute(() -> {
//                try {
//                    getChanges();
//                } catch (Exception e) {
//                    log.error("Got error getChanges: " + e.getMessage(), e);
//                }
//            });
//        }


    public void shutdown() {
        try {
            log.info("shutdown");
            shouldRun.set(false);
            stopCurrentRequest();
        } catch (Exception e) {
            log.error("Error in preDestroy", e);
        }
    }

    @Override
    public void run() {
        getChanges();
    }
}
