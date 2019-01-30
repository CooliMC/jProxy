package org.coolimc.ProxyServer.SocksProxy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocksProxyServer
{
    public static void main(String[] args)
    {
        //Setup testProxy
        SocksProxyServer myProxy = new SocksProxyServer(1080);

        //Enable AuthenticationMethods
        myProxy.enableAuthenticationMethod(AuthenticationMethod.NO_AUTHENTICATION_REQUIRED);
        myProxy.enableAuthenticationMethod(AuthenticationMethod.USERNAME_PASSWORD);

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

    //Variables
    private ServerSocket serverSocket;
    private AtomicBoolean running;

    private List<RequestHandler> serviceThreads;
    private Set<AuthenticationMethod> authenticationMethods;
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

    public void enableAuthenticationMethod(AuthenticationMethod toActivate)
    {
        //Server only supports these two methods at the time
        if(
            (toActivate == AuthenticationMethod.NO_AUTHENTICATION_REQUIRED) ||
            (toActivate == AuthenticationMethod.USERNAME_PASSWORD)
        ) this.authenticationMethods.add(toActivate);

        //Inform about no compatibility
        else System.out.println("AuthenticationMethod not supported by the server.");
    }

    public void disableAuthenticationMethod(AuthenticationMethod toDeactivate)
    {
        this.authenticationMethods.remove(toDeactivate);
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
            for(AuthenticationMethod tempServerAuth : authenticationMethods)
            {
                if(tempServerAuth.getByteCode() == tempClientAuth)
                    return tempClientAuth;
            }
        }

        //If there is no supported method return noAcceptableMethod
        return AuthenticationMethod.NO_ACCEPTABLE_METHODS.getByteCode();
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
                    if(serverAcceptedAuth == AuthenticationMethod.NO_ACCEPTABLE_METHODS.getByteCode())
                    {System.out.println("Close connection no acceptable auth.");
                        this.closeConnection(this.socket, null);
                        return;
                    }

                    //Check for usernameAndPassword
                    if(serverAcceptedAuth == AuthenticationMethod.USERNAME_PASSWORD.getByteCode())
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
                }

                System.out.println("Server ready to get sock connection packets");

                while(!socket.isClosed())
                {
                    if(this.proxyToClientInput.available() > 0)
                    {
                        byte[] toRead = this.readFromInputStream();
                        System.out.println("Request: " + Arrays.toString(toRead));
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
    }
}
