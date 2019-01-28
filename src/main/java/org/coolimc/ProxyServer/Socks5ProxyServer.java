package org.coolimc.ProxyServer;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Socks5ProxyServer
{
    public static void main(String[] args)
    {
        Socks5ProxyServer myProxy = new Socks5ProxyServer(1080);
        myProxy.listen();
    }

    private ServerSocket serverSocket;
    private AtomicBoolean running;

    private List<RequestHandler> serviceThreads;

    public Socks5ProxyServer(int port)
    {
        //Setup status variable
        this.running = new AtomicBoolean(true);

        //Try to setup the ServerSocket
        try {
            this.serverSocket = new ServerSocket(port);
        } catch(Exception e) {
            this.running.set(false);
            return;
        }

        //Try to setup lists
        this.serviceThreads = Collections.synchronizedList(new ArrayList<>());

        //User callback about status
        System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + " ...");
    }

    public void listen()
    {
        new Thread(() -> {
            while(running.get() && (serverSocket != null) && serverSocket.isBound())
            {
                try { serviceThreads.add(new RequestHandler(serverSocket.accept())); }
                catch(SocketTimeoutException e1) { /* Nothing to do here */ }
                catch (IOException e2) { e2.printStackTrace(); }
            }
        }).start();
    }

    private void closeServer()
    {
        //Close by variable
        System.out.println("\nClosing Server..");
        this.running.set(false);

        //Close all connectionThreads
        for(RequestHandler tempRequest : this.serviceThreads)
            tempRequest.running.set(false);

        //Close serverSocket
        try { this.serverSocket.close(); }
        catch(Exception e) { /* Nothing to do here */ }
    }

    private final class RequestHandler extends Thread
    {
        private final AtomicBoolean running;
        private final Socket connectionSocket;

        private RequestHandler(Socket connectionSocket)
        {
            //Setup internal handlers
            this.running = new AtomicBoolean(true);
            this.connectionSocket = connectionSocket;

            //TODO: CODE CODE CODE

            //Start RequestHandler
            this.start();
        }

        public void run()
        {

        }
    }
}
