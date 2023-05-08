package org.example;

import java.beans.EventHandler;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {

        String url = "http://localhost:8080/sytflix";
        List<String> events = new LinkedList<>();

        SSEClient sseClient = SSEClient.builder()
                            .url(url)
                            .eventHandler(eventText -> events.add(eventText))
                            .username("sytac")
                            .password("4p9g-Dv7T-u8fe-iz6y-SRW2")
                            .build();

        sseClient.start();

//        Client client = Client.builder()
//                .url(url)
//                .eventHandler(eventText -> events.add(eventText))
//                .username("sytac")
//                .password("4p9g-Dv7T-u8fe-iz6y-SRW2")
//                .build();
//
//
//
//        Thread thread = new Thread(client);
//        thread.start();
//
//        Timer timer = new Timer();
//        TimeOutTask timeOutTask = new TimeOutTask(thread, timer);
//        timer.schedule(timeOutTask, 10000);
    }
}