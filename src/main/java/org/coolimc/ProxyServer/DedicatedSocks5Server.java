package org.coolimc.ProxyServer;

import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicBoolean;

public class DedicatedSocks5Server
{
    public static void main(String[] args)
    {
        DedicatedSocks5Server myProxy = new DedicatedSocks5Server(8085);
        //myProxy;
    }

    private ServerSocket serverSocket;
    private AtomicBoolean running;

    public DedicatedSocks5Server(int port)
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
    }
}
