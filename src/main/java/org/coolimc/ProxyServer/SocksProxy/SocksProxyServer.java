package org.coolimc.ProxyServer.SocksProxy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocksProxyServer
{
    public static void main(String[] args)
    {
        SocksProxyServer myProxy = new SocksProxyServer(1080);
        myProxy.listen();
    }

    private ServerSocket serverSocket;
    private AtomicBoolean running;

    private List<RequestHandler> serviceThreads;

    public SocksProxyServer(int port)
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
        private final Socket socket;

        private InputStream proxyToClientInput;
        private OutputStream proxyToClientOutput;
        private Thread clientToServer, serverToClient;

        private RequestHandler(Socket connectionSocket)
        {
            //Setup internal handlers
            this.running = new AtomicBoolean(true);
            this.socket = connectionSocket;

            try {
                //Setup timeout for disconnects
                this.socket.setSoTimeout(5000);

                //Setup input and output stream
                this.proxyToClientInput = socket.getInputStream();
                this.proxyToClientOutput = socket.getOutputStream();
            } catch(Exception e) {
                this.running.set(false);
            }

            //Start RequestHandler
            this.start();
        }

        public void run()
        {
            try {
                //Read the first line for more information about the request
                byte[] firstRead = this.proxyToClientInput.readAllBytes();

                //Check for corrupt packets
                if((firstRead.length < 3) || !((firstRead[0] == 0x04) || (firstRead[0] == 0x05)))
                    return;

                //Check for Socks-Version5 and AuthPacket
                if(firstRead[0] == 0x05)
                {
                    //Check if packet corrupt
                    if(firstRead.length < (2 + firstRead[1])) return;

                    //Get all authFunctions from authRequest
                    byte[] authFunctions = Arrays.copyOfRange(firstRead, 2, firstRead[1]);


                }

                System.out.println("Request: " + Arrays.toString(firstRead));
                //Check for the right header length
                //if(request.length < 3) return;

                //Check for connection type and call right function
                //if(this.isHttpsTunnel(request[0])) this.connectTunnel(request[1]);
                //else this.connectRelay(request[1]);

            } catch(Exception e) {
                this.closeConnection(this.socket, null);
            }
        }

        private void closeConnection(Socket toClose1, Socket toClose2)
        {
            //Stop Loops
            this.running.set(false);

            //Close Streams
            try { if(this.proxyToClientInput != null) this.proxyToClientInput.close(); }
            catch(Exception e) { /* Nothing to do here */ }

            try { if(this.proxyToClientOutput != null) this.proxyToClientOutput.close(); }
            catch(Exception e) { /* Nothing to do here */ }

            //Close Sockets
            try { if(toClose1 != null) toClose1.close(); }
            catch(Exception e) { /* Nothing to do here */ }

            try { if(toClose2 != null) toClose2.close(); }
            catch(Exception e) { /* Nothing to do here */ }

            //Remove from connectionList
            serviceThreads.remove(this);
        }
    }
}
