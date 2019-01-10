package org.coolimc.ProxyServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SockServer
{
    private ServerSocket ssok;
    private SocketServerThread worker;
    private List<SockClientHandler> connections;

    private SockServer(int serverPort) throws Exception
    {
        //Setup internal list
        this.connections = Collections.synchronizedList(new ArrayList<>());

        //Setup internal variables
        this.ssok = new ServerSocket(serverPort);
        this.worker = new SocketServerThread();

        //Setup settings
        this.ssok.setSoTimeout(100);
    }

    public static SockServer getSockServer(int serverPort)
    {
        try { return new SockServer(serverPort); }
        catch(Exception e) { return null; }
    }

    public void start()
    {
        //Starting WorkerThread
        this.worker.start();
    }


    private final class SocketServerThread extends Thread
    {
        private final AtomicBoolean running = new AtomicBoolean(true);

        public void run()
        {
            while((ssok != null) && ssok.isBound() && this.running.get())
            {
                //Wait for incoming connection
                try { connections.add(new SockClientHandler(ssok.accept())); }

                //Do nothing when timeout
                catch(SocketTimeoutException e1) { /* Nothing to do here */ }

                //If there is another error print it
                catch (IOException e2) { e2.printStackTrace(); }
            }
        }

        private void stopThread()
        {
            this.running.set(false);
        }
    }

    public static void main(String[] args)
    {
        SockServer temP = SockServer.getSockServer(1080);

        if(temP != null) temP.start();
        else System.err.println("Error no Server");
    }
}
