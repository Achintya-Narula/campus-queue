package dev.achu.campusqueue;

import java.net.InetSocketAddress;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        var server = new CampusQueueHttpServer(new CampusQueueService(), new InetSocketAddress("127.0.0.1", port));
        server.start();
        System.out.println("CampusQueue is running at http://127.0.0.1:" + server.port());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
