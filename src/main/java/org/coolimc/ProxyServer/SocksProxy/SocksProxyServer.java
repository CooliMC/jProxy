package org.coolimc.ProxyServer.SocksProxy;

import org.coolimc.ProxyServer.ProxyUtils.Utils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;
import java.net.*;
import java.io.*;

public class SocksProxyServer
{
    public static void main(String[] args)
    {
        //Setup testProxy
        SocksProxyServer myProxy = new SocksProxyServer(1080);

        //Enable AuthenticationMethods
        myProxy.enableAuthenticationMethod(Socks5Authentication.NO_AUTHENTICATION_REQUIRED);
        myProxy.enableAuthenticationMethod(Socks5Authentication.USERNAME_PASSWORD);

        //Enable ConnectionMethods
        myProxy.enableConnectionMode(SocksCommand.ESTABLISH_TCP_CONNECTION);
        myProxy.enableConnectionMode(SocksCommand.ESTABLISH_TCP_PORT_SERVER);
        myProxy.enableConnectionMode(SocksCommand.ESTABLISH_UDP_CONNECTION);

        //Add UserProfiles
        myProxy.addUserProfile("CooliMC", "345678");
        myProxy.addUserProfile("Kanisterkopf", "MC-Donalds");

        //Start the Server
        myProxy.listen();
    }

    //Definitions
    private static final int DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH = 3;
    private static final int DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH = 2;
    private static final int DEFAULT_SOCKS5_AUTH_TIMEOUT_MS = 2500;

    private static final int DEFAULT_SOCKS5_HEADER_LENGTH = 8;
    private static final int DEFAULT_SOCKS4_HEADER_LENGTH = 9;


    //Variables
    private ServerSocket serverSocket;
    private AtomicBoolean running;

    private List<RequestHandler> serviceThreads;
    private Set<Socks5Authentication> authenticationMethods;
    private Set<SocksCommand> connectionModes;
    private Map<String, String> userProfiles;

    public SocksProxyServer(int port)
    {
        //Setup status variable
        this.running = new AtomicBoolean(true);

        //Try to setup the ServerSocket
        try { this.serverSocket = new ServerSocket(port); }
        catch(Exception e) { this.running.set(false); return; }

        //Try to setup lists
        this.serviceThreads = Collections.synchronizedList(new ArrayList<>());
        this.authenticationMethods = Collections.synchronizedSet(new HashSet<>());
        this.connectionModes = Collections.synchronizedSet(new HashSet<>());
        this.userProfiles = Collections.synchronizedMap(new HashMap<>());

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

    public void enableAuthenticationMethod(Socks5Authentication toActivate)
    {
        //Server only supports these two methods at the time
        if(
            (toActivate == Socks5Authentication.NO_AUTHENTICATION_REQUIRED) ||
            (toActivate == Socks5Authentication.USERNAME_PASSWORD)
        ) this.authenticationMethods.add(toActivate);

        //Inform about no compatibility
        else System.out.println("AuthenticationMethod not supported by the server.");
    }

    public void disableAuthenticationMethod(Socks5Authentication toDeactivate)
    {
        this.authenticationMethods.remove(toDeactivate);
    }

    public void enableConnectionMode(SocksCommand toActivate)
    {
        this.connectionModes.add(toActivate);
    }

    public void disableConnectionMode(SocksCommand toDeactivate)
    {
        this.connectionModes.remove(toDeactivate);
    }

    public void addUserProfile(String username, String password)
    {
        this.userProfiles.put(username, ((password != null) ? password : ""));
    }

    public void removeUserProfile(String username)
    {
        this.userProfiles.remove(username);
    }

    public void closeServer()
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

    private byte isAuthSupported(byte[] toCheck)
    {
        //Loop through all client and server supported AuthenticationMethods and check for a match
        for(byte tempClientAuth : toCheck)
        {
            for(Socks5Authentication tempServerAuth : authenticationMethods)
            {
                if(tempServerAuth.getByteCode() == tempClientAuth)
                    return tempClientAuth;
            }
        }

        //If there is no supported method return noAcceptableMethod
        return Socks5Authentication.NO_ACCEPTABLE_METHODS.getByteCode();
    }

    private boolean isConnectionModeSupported(SocksCommand toCheck)
    {
        //Loop through all server supported ConnectionModes and check for a match
        return this.connectionModes.contains(toCheck);
    }

    private boolean checkCredentials(String username, String password)
    {
        String result = this.userProfiles.get(username);
        return ((result != null) && result.equals(password));
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
                System.out.println("Incoming Request: " + Arrays.toString(firstRead));
                //Check for corrupt packet length
                if(firstRead.length < SocksProxyServer.DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH)
                    return;

                //Check for corrupt packet header
                if(
                    (firstRead[0] != SocksVersion.Socks4.getIntCode()) &&
                    (firstRead[0] != SocksVersion.Socks5.getIntCode())
                ) return;

                //Check for Socks-Version5 and AuthPacket
                if(firstRead[0] == SocksVersion.Socks5.getIntCode())
                {
                    //Get int of packetLength
                    int realPacketLength = (SocksProxyServer.DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH + firstRead[1]);

                    //Check if packet size is corrupt
                    if (firstRead.length < realPacketLength)
                        return;

                    //Get all authFunctions from authRequest and check SocksServer for AuthMethods
                    byte serverAcceptedAuth = isAuthSupported(Arrays.copyOfRange(
                        firstRead, SocksProxyServer.DEFAULT_SOCKS5_AUTH_PRE_HEADER_LENGTH, realPacketLength
                    ));

                    //Build the serverAuthAnswer
                    byte[] serverAuthAnswer = {
                        SocksVersion.Socks5.getByteCode(),
                        serverAcceptedAuth
                    };

                    //Send the answer to the client
                    this.proxyToClientOutput.write(serverAuthAnswer);
                    this.proxyToClientOutput.flush();

                    //Check for rejection
                    if(serverAcceptedAuth == Socks5Authentication.NO_ACCEPTABLE_METHODS.getByteCode())
                    {System.out.println("Close connection no acceptable auth.");
                        this.closeConnection(this.socket, null);
                        return;
                    }

                    //Check for usernameAndPassword
                    if(serverAcceptedAuth == Socks5Authentication.USERNAME_PASSWORD.getByteCode())
                    {
                        //Wait for login packet with username and password
                        byte[] authPacket = this.readFromInputStream(DEFAULT_SOCKS5_AUTH_TIMEOUT_MS);

                        //Check for supportedSubversion
                        if(authPacket[0] != 0x01)
                            return;

                        //Check for corrupt packet header
                        if(authPacket.length < SocksProxyServer.DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH)
                            return;

                        //Extract username and password length
                        int usernameLength = authPacket[1];
                        int passwordLength = authPacket[2+ usernameLength];

                        //Check for correct packet size
                        if((SocksProxyServer.DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH + usernameLength + passwordLength) != authPacket.length)
                            return;

                        //Extract username and password string
                        String username = ((usernameLength > 0) ? new String(Arrays.copyOfRange(
                            authPacket, 2, (2 + usernameLength)
                        )) : "");
                        String password = ((passwordLength > 0) ? new String(Arrays.copyOfRange(
                            authPacket, (3 + usernameLength), (3 + usernameLength + passwordLength)
                        )) : "");

                        //Build the answer for the client
                        byte[] authAnswer = {0x01, ((byte) (checkCredentials(username, password) ? 0x00 : 0x01))};

                        //Send the answer to the client
                        this.proxyToClientOutput.write(authAnswer);
                        this.proxyToClientOutput.flush();

                        //Check for false credential and disconnect
                        if(authAnswer[1] != 0x00)
                        {
                            System.out.println("Wrong Login - Disconnecting");

                            this.closeConnection(this.socket, null);
                            return;
                        }
                    }

                    //Read next data packet for "normal" socks packet
                    firstRead = this.readFromInputStream(SocksProxyServer.DEFAULT_SOCKS5_AUTH_TIMEOUT_MS);
                    System.out.println("Incoming Request2: " + Arrays.toString(firstRead));

                    //Check for corrupt packet length
                    if(firstRead.length < SocksProxyServer.DEFAULT_SOCKS5_AUTH_HEADER_MIN_LENGTH)
                        return;

                    //Check for corrupt packet header
                    if(
                        (firstRead[0] != SocksVersion.Socks4.getIntCode()) &&
                        (firstRead[0] != SocksVersion.Socks5.getIntCode())
                    ) return;
                }

                //Check for valid command flag
                if(
                    (firstRead[1] != SocksCommand.ESTABLISH_TCP_CONNECTION.getIntCode()) &&
                    (firstRead[1] != SocksCommand.ESTABLISH_TCP_PORT_SERVER.getIntCode()) &&
                    (firstRead[1] != SocksCommand.ESTABLISH_UDP_CONNECTION.getIntCode())
                ) return;

                //Check if it's a Socks5 or Socks4 Request
                if(firstRead[0] == SocksVersion.Socks5.getByteCode())
                {
                    //Check for corrupt packet length
                    if(firstRead.length < SocksProxyServer.DEFAULT_SOCKS5_HEADER_LENGTH)
                        return;

                    //Check command flag if it's a tcp-connection or tcp-server or udp-connection
                    if(firstRead[1] == SocksCommand.ESTABLISH_TCP_CONNECTION.getIntCode())
                    {

                    } else if(firstRead[1] == SocksCommand.ESTABLISH_TCP_PORT_SERVER.getIntCode()) {

                    } else {

                    }

                } else {
                    //Check for corrupt packet length
                    if(firstRead.length < SocksProxyServer.DEFAULT_SOCKS4_HEADER_LENGTH)
                        return;

                    //Check for valid command flag
                    if(firstRead[1] == SocksCommand.ESTABLISH_UDP_CONNECTION.getIntCode())
                    {
                        System.out.println("UDP-Connection Proxy not support in Socks4.");

                        this.closeConnection(this.socket, null);
                        return;
                    }

                    //Check if last byte is termination byte
                    if(firstRead[firstRead.length - 1] != 0x00)
                        return;

                    //Get different Fields
                    byte[] readRest = Arrays.copyOfRange(firstRead, 8, (firstRead.length - 1));
                    byte[] destPort = Arrays.copyOfRange(firstRead, 2, 4);
                    byte[] destAddr = Arrays.copyOfRange(firstRead, 4, 8);
                    byte[] userIdent, domainName;

                    //Get CutByte
                    int cutByte = Utils.indexOf(readRest, (byte) 0x00);

                    //Check if there is a corrupt header and get the rest
                    if(cutByte >= 0)
                    {
                        domainName = Arrays.copyOfRange(readRest, (cutByte + 1), readRest.length);
                        userIdent = Arrays.copyOf(readRest, cutByte);
                    } else {
                        domainName = new byte[0];
                        userIdent = readRest;
                    }

                    //Get destination port and address
                    int destinationPort = Utils.calcPortByBytes(destPort[0], destPort[1]);

                    //Check if its a normal ip4 or a domain
                    InetAddress destinationAddress = (
                        (destAddr[0] == 0x00 && destAddr[1] == 0x00 && destAddr[2] == 0x00 && destAddr[3] != 0x00) ?
                            (Utils.getInetAddressByName(new String(domainName))) :
                            (Utils.getInetAddressByBytes(destAddr))
                    );

                    //Check command flag if it's a tcp-connection or tcp-server
                    if(firstRead[1] == SocksCommand.ESTABLISH_TCP_CONNECTION.getIntCode())
                    {
                        //Check if the command is supported or the domain or ip4 is valid
                        if(!isConnectionModeSupported(SocksCommand.ESTABLISH_TCP_CONNECTION) || (destinationAddress == null))
                        {
                            //Build connectionAnswer
                            byte[] connectionAnswer = new byte[]
                            {
                                0x00, Socks4Reply.REQUEST_REJECTED_OR_FAILED.getByteCode(),
                                destPort[0], destPort[1],
                                destAddr[0], destAddr[1], destAddr[2], destAddr[3]
                            };

                            //Send server reject to client
                            this.proxyToClientOutput.write(connectionAnswer);
                            this.proxyToClientOutput.flush();

                            //Close the connection
                            this.closeConnection(this.socket, null);
                            return;
                        }

                        //Try to connect to the given Address
                        Socket tempProxyToServer = new Socket(destinationAddress, destinationPort);
                        tempProxyToServer.setSoTimeout(5000);

                        //Check if the connection was successful
                        if(!tempProxyToServer.isConnected())
                        {
                            //Build connectionAnswer
                            byte[] connectionAnswer = new byte[]
                            {
                                0x00, Socks4Reply.REQUEST_REJECTED_OR_FAILED.getByteCode(),
                                destPort[0], destPort[1],
                                destAddr[0], destAddr[1], destAddr[2], destAddr[3]
                            };

                            //Send server reject to client
                            this.proxyToClientOutput.write(connectionAnswer);
                            this.proxyToClientOutput.flush();

                            //Close the connection
                            this.closeConnection(this.socket, null);
                            return;
                        }

                        //Inform client that connection is established
                        byte[] tempDestAdr = destinationAddress.getAddress();
                        byte[] connectionAnswer = new byte[]
                        {
                            0x00, Socks4Reply.REQUEST_GRANTED.getByteCode(),
                            destPort[0], destPort[1],
                            tempDestAdr[0], tempDestAdr[1], tempDestAdr[2], tempDestAdr[3]
                        };

                        //Send server accept to the client
                        this.proxyToClientOutput.write(connectionAnswer);
                        this.proxyToClientOutput.flush();

                        //Create ProxyToServerThread
                        this.clientToServer = new RelayThread(this.socket, tempProxyToServer);
                        this.serverToClient = new RelayThread(tempProxyToServer, this.socket);

                        //Start bidirectional threads
                        this.clientToServer.start();
                        this.serverToClient.start();

                        this.serverToClient.join();
                        this.clientToServer.join();

                        this.closeConnection(this.socket, tempProxyToServer);

                    } else {
                        //Check if the command is supported or the ip4 is valid
                        if(!isConnectionModeSupported(SocksCommand.ESTABLISH_TCP_PORT_SERVER) || (destinationAddress == null))
                        {
                            //Build connectionAnswer
                            byte[] connectionAnswer = new byte[]
                            {
                                0x00, Socks4Reply.REQUEST_REJECTED_OR_FAILED.getByteCode(),
                                destPort[0], destPort[1],
                                destAddr[0], destAddr[1], destAddr[2], destAddr[3]
                            };

                            //Send server reject to client
                            this.proxyToClientOutput.write(connectionAnswer);
                            this.proxyToClientOutput.flush();

                            //Close the connection
                            this.closeConnection(this.socket, null);
                            return;
                        }

                        //Try to bind a socket to the given Port
                        ServerSocket bindSocket = null;

                        //Check if the serverSocket port is available and bind
                        if(Utils.isTcpPortAvailable(destinationPort))
                        {
                            try { bindSocket = new ServerSocket(destinationPort); }
                            catch(Exception e) { bindSocket = null; }
                        }

                        //Check if the port bind was successful
                        if(bindSocket == null)
                            bindSocket = new ServerSocket(0);

                        //Set timeout as default 2 minute
                        bindSocket.setSoTimeout(120000);

                        //Get the port of the serverSocketBind
                        byte[] bindPort = Utils.calcPortByInt(bindSocket.getLocalPort());
                        byte[] bindAddress = (Utils.anyLocalAddress().getAddress());

                        //Inform client that bind is established
                        byte[] connectionAnswer = new byte[]
                        {
                            0x00, Socks4Reply.REQUEST_GRANTED.getByteCode(),
                            bindPort[0], bindPort[1],
                            bindAddress[0], bindAddress[1], bindAddress[2], bindAddress[3]
                        };

                        //Send server accept to the client
                        this.proxyToClientOutput.write(connectionAnswer);
                        this.proxyToClientOutput.flush();

                        //Try to connect to the given Address
                        Socket tempProxyToServer = bindSocket.accept();
                        tempProxyToServer.setSoTimeout(5000);

                        //Create ProxyToServerThread
                        this.clientToServer = new RelayThread(this.socket, tempProxyToServer);
                        this.serverToClient = new RelayThread(tempProxyToServer, this.socket);

                        //Start bidirectional threads
                        this.clientToServer.start();
                        this.serverToClient.start();

                        this.serverToClient.join();
                        this.clientToServer.join();

                        this.closeConnection(this.socket, tempProxyToServer);
                    }
                }

                System.out.println("Ende Request");

            } catch(SocketTimeoutException e1) {
                /* Nothing to do here */
            } catch(Exception e2) {
                e2.printStackTrace();
                this.closeConnection(this.socket, null);
            }

            //Remove from RequestHandler List
            serviceThreads.remove(this);
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

        private byte[] readFromInputStream() throws IOException
        {
            //Create new byte[] with length of bytes in buffer
            byte[] toRet = new byte[this.proxyToClientInput.available()];

            //Read all of these bytes into the byte[]
            this.proxyToClientInput.read(toRet);

            //Return given byte[]
            return toRet;
        }

        private byte[] readFromInputStream(int timeout) throws IOException
        {
            //Setup method tempVariables
            long tempTime = (System.currentTimeMillis() + timeout);
            byte[] toRet = this.readFromInputStream();

            //Check for input as long as wanted (with timeout)
            while(toRet.length == 0)
            {
                //Check for timeout
                if((timeout > 0) || (tempTime <= System.currentTimeMillis()))
                    throw new SocketTimeoutException("Read timed out");

                //If there is no timeout check for new data
                toRet = this.readFromInputStream();
            }

            //Return the result
            return toRet;
        }

        private final class RelayThread extends Thread
        {
            private final Socket fromSocket, toSocket;

            private RelayThread(Socket from, Socket to)
            {
                this.fromSocket = from;
                this.toSocket = to;
            }

            public void run()
            {
                //Get Packages from Server and Send to Client
                try {
                    InputStream fromServer = this.fromSocket.getInputStream();
                    OutputStream toClient = this.toSocket.getOutputStream();

                    byte[] buffer = new byte[4096];
                    int read = 0;

                    while(running.get() && !this.fromSocket.isClosed() && !this.toSocket.isClosed() && (read >= 0))
                    {
                        try {
                            read = fromServer.read(buffer);

                            if(read > 0)
                            {
                                toClient.write(buffer, 0, read);

                                if(fromServer.available() < 1)
                                    toClient.flush();
                            }

                        } catch(Exception e1) {
                            /* Nothing to do here */
                        }
                    }
                } catch(Exception e2) {
                    /* Nothing to do here */
                }

                //Stop the ProxyConnection
                closeConnection(fromSocket, toSocket);
            }
        }
    }
}
