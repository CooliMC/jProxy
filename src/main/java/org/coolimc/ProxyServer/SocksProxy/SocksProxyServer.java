package org.coolimc.ProxyServer.SocksProxy;

import javax.imageio.IIOException;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocksProxyServer
{
    public static void main(String[] args)
    {
        SocksProxyServer myProxy = new SocksProxyServer(1080);
        myProxy.listen();
    }

    //Definitions
    private static final int DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH = 3;
    private static final int DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH = 2;

    //Variables
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

        private DataInputStream proxyToClientInput;
        private DataOutputStream proxyToClientOutput;
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
                this.proxyToClientInput = new DataInputStream(socket.getInputStream());
                this.proxyToClientOutput = new DataOutputStream(socket.getOutputStream());
            } catch(Exception e) {
                this.running.set(false);
            }

            //Start RequestHandler
            this.start();
        }

        public void run()
        {
            try {
                //Read the initial call for more information
                byte[] firstRead = this.readFromInputStream();

                //Check for corrupt packet length
                if (firstRead.length < SocksProxyServer.DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH)
                    return;

                //Check for corrupt packet header
                if (
                    (firstRead[0] != SocksVersion.Socks4.getIntCode()) &&
                    (firstRead[0] != SocksVersion.Socks5.getIntCode())
                ) return;

                //Check for Socks-Version5 and AuthPacket
                if (firstRead[0] == SocksVersion.Socks5.getIntCode()) {
                    //Get int of packetLength
                    int realPacketLength = (SocksProxyServer.DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH + firstRead[1]);

                    //Check if packet size is corrupt
                    if (firstRead.length < realPacketLength)
                        return;

                    //Get all authFunctions from authRequest
                    byte[] authFunctions = Arrays.copyOfRange(
                        firstRead, SocksProxyServer.DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH, realPacketLength
                    );

                    //Check SocksServer for AuthMethods
                    //TODO : CHECK CHECK

                    //Do a callback
                    this.proxyToClientOutput.write(ByteBuffer.allocate(2).put((byte) 0x05).put((byte) 0x02).array());
                    this.proxyToClientOutput.flush();
                }

                while(!socket.isClosed())
                {
                    if(this.proxyToClientInput.available() > 0)
                    {
                        byte[] toRead = this.readFromInputStream();
                        System.out.println("Request2: " + Arrays.toString(toRead));
                    }
                }

                System.out.println("Ende Request");
                //Check for the right header length
                //if(request.length < 3) return;

                //Check for connection type and call right function
                //if(this.isHttpsTunnel(request[0])) this.connectTunnel(request[1]);
                //else this.connectRelay(request[1]);

            } catch(SocketTimeoutException e1) {
                /* Nothing to do here */
            } catch(Exception e2) {
                e2.printStackTrace();
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

        private byte[] readFromInputStream()
        {
            try {
                //Create new byte[] with length of bytes in buffer
                byte[] toRet = new byte[this.proxyToClientInput.available()];

                //Read all of these bytes into the byte[]
                this.proxyToClientInput.read(toRet);

                //Return given byte[]
                return toRet;
            } catch (Exception e) {
                //Return empty byte[]
                return (new byte[0]);
            }

        }
    }
}
