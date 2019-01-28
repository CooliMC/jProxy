package org.coolimc.ProxyServer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DedicatedProxyServer
{
    public static void main(String[] args)
    {
        DedicatedProxyServer myProxy = new DedicatedProxyServer(8085);
        myProxy.listen();
    }

    private ServerSocket serverSocket;
    private AtomicBoolean running;

    private Set<String> blockedSites;
    private List<String> blockedSource;
    private List<RequestHandler> serviceThreads;

    public DedicatedProxyServer(int port)
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
        this.blockedSites = Collections.synchronizedSet(new HashSet<>());
        this.blockedSource = Collections.synchronizedList(new ArrayList<>());
        this.serviceThreads = Collections.synchronizedList(new ArrayList<>());

        //Load list for sources of BlockedUrls
        this.loadBlockSites();

        //Start ConsoleInterface
        new ConsoleInterface().start();

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

    private void loadBlockSites()
    {
        File blockedSitesSourceFile = new File("blockedSitesSource.txt");
        File blockedSitesListFile = new File("blockedSitesList.txt");
        String tempLine;

        //Try to read or create blocked sites source
        if(!blockedSitesSourceFile.exists())
        {
            //Inform user
            System.out.println("No blocked sites source file found - creating new file");

            //Create file and inform user
            try { blockedSitesSourceFile.createNewFile(); }
            catch(Exception e1) { /* Nothing to do here */ }
        } else {
            //Create a fileStream for file to list converting
            this.readFileToCollection(blockedSitesSourceFile, this.blockedSource);
        }

        //Try to read create and fill blocked sites list
        if(!blockedSitesListFile.exists())
        {
            //Inform user
            System.out.println("No blocked sites list file found - creating new file");

            //Create file and inform user
            try { blockedSitesListFile.createNewFile(); }
            catch(Exception e1) { /* Nothing to do here */ }
        } else {
            //Create a fileStream for file to list converting
            this.readFileToCollection(blockedSitesListFile, this.blockedSites);
        }

        //Fill blockedSitesList with entries from sourceFiles
        for(String tempSource : this.blockedSource)
        {
            System.out.println("Trying to fetch URLs from " + tempSource);

            try {
                //Create connection and setup BufferedReader
                HttpURLConnection tempCon = ((HttpURLConnection) (new URL(tempSource)).openConnection());
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(tempCon.getInputStream()));

                while((tempLine = inputReader.readLine()) != null)
                {
                    //Remove whiteSpace and tab before and after and toLowerCase
                    tempLine = (tempLine.replaceAll("\t", "").trim().toLowerCase());

                    //Check for valid URL or IP
                    if(
                            (tempLine.length() < 3) ||
                                    (tempLine.startsWith("#")) ||
                                    (tempLine.contains("localhost")) ||
                                    (tempLine.contains("127.0.0.1"))
                    ) continue;

                    //Check for split domain with alternative ip
                    if(tempLine.contains(" "))
                        tempLine = (tempLine.substring(tempLine.indexOf(" ")).trim());

                    //Check for a comment at the end
                    if(tempLine.contains("#"))
                        tempLine = (tempLine.substring(0, tempLine.indexOf("#")).trim());

                    //Check for semicolon at the domain end
                    if(tempLine.contains(";"))
                        tempLine = (tempLine.substring(0, tempLine.indexOf(";")).trim());

                    this.blockedSites.add(tempLine);
                }
            } catch (Exception e1) {
                /* Nothing to do here */
            }
        }
    }

    private boolean isBlocked(String url)
    {
        //Check for valid url
        if(url == null) return false;

        //Check if http in front of url
        return this.blockedSites.contains(url.toLowerCase());
    }

    private void readFileToCollection(File sourceFile, Collection<String> destList)
    {
        //Create temporary String for Lines
        String tempLine;

        //Create a fileStream for file to list converting
        try(BufferedReader fileReader = new BufferedReader(new FileReader(sourceFile)))
        {
            while((tempLine = fileReader.readLine()) != null)
            {
                //Trim tempLine
                tempLine = tempLine.trim();

                //Check if valid
                if(tempLine.length() > 2)
                    destList.add(tempLine.toLowerCase());
            }
        } catch(Exception e1) {
            /* Nothing to do here */
        }
    }

    private final class ConsoleInterface extends Thread
    {
        public void run()
        {
            Scanner scanner = new Scanner(System.in);
            String command;

            while(running.get())
            {
                System.out.println("Enter new site to block, or type \"blocked\" to see blocked sites, \"connections\" to see current connection count, or \"close\" to close server.");
                command = (scanner.nextLine().toLowerCase());

                if(command.equals("blocked"))
                {
                    System.out.println("\nCurrently Blocked Sites");

                    for(String tempUrl : blockedSites)
                        System.out.println(tempUrl);

                    System.out.println();
                } else if(command.equals("clear")) {
                    System.out.println("\nCurrently Blocked Sites cleared");
                } else if(command.equals("connections")) {
                    System.out.println("\nCurrent Connection Count: " + serviceThreads.size() + " \n");
                    System.out.println();
                } else if(command.equals("close")) {
                    running.set(false);
                    closeServer();
                } else if(command.length() > 3) {
                    blockedSites.add(command);
                    System.out.println("\n" + command + " blocked successfully \n");
                }
            }

            scanner.close();
        }
    }

    private final class RequestHandler extends Thread
    {
        private final AtomicBoolean running;
        private final Socket socket;

        private BufferedReader proxyToClientInput;
        private BufferedWriter proxyToClientOutput;
        private Thread clientToServer, serverToClient;

        private RequestHandler(Socket requestSocket)
        {
            this.running = new AtomicBoolean(true);
            this.socket = requestSocket;

            try {
                //Setup timeout for disconnects
                this.socket.setSoTimeout(5000);

                //Setup input and output stream
                this.proxyToClientInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.proxyToClientOutput = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            } catch(Exception e) {
                this.running.set(false);
            }

            //Start itself
            this.start();
        }

        public void run()
        {
            try {
                //Read the first line for more information about the proxy type
                String[] request = (this.proxyToClientInput.readLine()).split(" ");
                System.out.println("Request: " + Arrays.toString(request));
                //Check for the right header length
                if(request.length < 3) return;

                //Check for connection type and call right function
                if(this.isHttpsTunnel(request[0])) this.connectTunnel(request[1]);
                else this.connectRelay(request[1]);

            } catch(Exception e) {
                this.closeConnection(this.socket, null);
            }
        }

        private boolean isHttpsTunnel(String conType)
        {
            return (conType.toLowerCase().equals("connect"));
        }

        private void connectTunnel(String remoteAddress)
        {
            //Get the RemoteAddress for Tunneling
            String remoteUrl = remoteAddress;
            int remotePort = 80;

            if(remoteAddress.contains(":"))
            {
                //Split the string for check of correct header
                String[] tempRemoteUrl = remoteAddress.split(":");

                //Check for right header
                if(tempRemoteUrl.length > 2) return;

                //Set remoteUrl and remotePort
                remotePort = Integer.valueOf(tempRemoteUrl[1]);
                remoteUrl = tempRemoteUrl[0];
            }

            //Check if the RemoteAddress is on the BlockList
            if(isBlocked(remoteUrl))
            {
                System.out.println("Blocked address '" + remoteUrl + "'");

                this.blockedSiteRequested();
                this.closeConnection(this.socket, null);

                return;
            }

            //No blocked address
            try {
                Socket tempProxyToServer = new Socket(remoteUrl, remotePort);
                tempProxyToServer.setSoTimeout(5000);

                //Check if connection is possible
                if(!tempProxyToServer.isConnected())
                {
                    System.out.println("Can't connect to remote address.");

                    this.closeConnection(this.socket, null);
                    return;
                }

                //Inform client that connection is established
                this.tunnelRequested();

                //Create ProxyToServerThread
                this.clientToServer = new RelayThread(this.socket, tempProxyToServer);
                this.serverToClient = new RelayThread(tempProxyToServer, this.socket);

                //Start bidirectional threads
                this.clientToServer.start();
                this.serverToClient.start();

                this.serverToClient.join();
                this.clientToServer.join();

                this.closeConnection(this.socket, tempProxyToServer);

            } catch (Exception e) {
                this.closeConnection(this.socket, null);
            }
        }

        private void connectRelay(String remoteAddress)
        {
            try {
                //Check if the RemoteAddress is on the BlockList
                if(isBlocked(remoteAddress))
                {
                    System.out.println("Blocked address '" + remoteAddress + "'");

                    this.blockedSiteRequested();
                    this.closeConnection(this.socket, null);

                    return;
                }

                //Get URL of remoteAddress
                URL remoteURL = new URL(remoteAddress);

                //Create a connection to the remote Address
                HttpURLConnection proxyToServer = ((HttpURLConnection) remoteURL.openConnection());

                //Optional
                proxyToServer.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:64.0) Gecko/20100101 Firefox/64.0");
                proxyToServer.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                proxyToServer.setRequestProperty("Content-Language", "de-DE");

                //Setup connection
                proxyToServer.setUseCaches(false);
                proxyToServer.setDoOutput(true);

                if(proxyToServer.getResponseCode() != 200)
                {
                    this.notFoundSiteRequest();
                    this.closeConnection(this.socket, null);

                    return;
                }

                //Inform client of success
                this.relayRequested();

                //Send Server Callback to Client
                InputStream fromServer = proxyToServer.getInputStream();
                OutputStream toClient = this.socket.getOutputStream();

                byte[] buffer = new byte[4096];
                int read = 0;

                while(running.get() && !this.socket.isClosed() && (read >= 0))
                {
                    try {
                        read = fromServer.read(buffer);

                        if(read > 0)
                        {
                            toClient.write(buffer, 0, read);

                            if(fromServer.available() < 1)
                                toClient.flush();
                        }
                    } catch (Exception e1) {
                        /* Nothing to do here */
                    }
                }

                //Close connections
                this.closeConnection(this.socket, null);
                proxyToServer.disconnect();

            } catch(Exception e2) {
                e2.printStackTrace();
                this.closeConnection(this.socket, null);
            }
        }

        private void blockedSiteRequested()
        {
            try {
                this.proxyToClientOutput.write(
                        "HTTP/1.0 403 Access Forbidden \n" +
                                "User-Agent: ProxyServer/1.0\n" +
                                "\r\n"
                );

                this.proxyToClientOutput.flush();
                this.proxyToClientOutput.close();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void notFoundSiteRequest()
        {
            try {
                this.proxyToClientOutput.write(
                        "HTTP/1.0 404 NOT FOUND\n" +
                                "Proxy-agent: ProxyServer/1.0\n" +
                                "\r\n"
                );

                this.proxyToClientOutput.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void tunnelRequested()
        {
            try {
                this.proxyToClientOutput.write(
                        "HTTP/1.0 200 Connection established\r\n" +
                                "Proxy-Agent: ProxyServer/1.0\r\n" +
                                "\r\n"
                );

                this.proxyToClientOutput.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        private void relayRequested()
        {
            try {
                this.proxyToClientOutput.write(
                        "HTTP/1.0 200 OK\n" +
                                "Proxy-agent: ProxyServer/1.0\n" +
                                "\r\n"
                );

                this.proxyToClientOutput.flush();
            } catch(Exception e) {
                e.printStackTrace();
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