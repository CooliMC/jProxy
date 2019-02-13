package org.coolimc.ProxyServer.ProxyUtils;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

public class Utils
{
    private Utils()
    {
        /* Do nothing instead of being private. */
    }

    public static InetAddress anyLocalAddress()
    {
        return ((new InetSocketAddress(0)).getAddress());
    }

    public static InetAddress getInetAddressByBytes(byte[] toBuild)
    {
        try { return InetAddress.getByAddress(toBuild); }
        catch(Exception e) { return null; }
    }

    public static InetAddress getInetAddressByName(String toResolve)
    {
        try { return InetAddress.getByName(toResolve); }
        catch(Exception e) { return null; }
    }

    public static int calcPortByBytes(byte hByte, byte lByte)
    {
        return ((Utils.getUnsignedInt(hByte) * 256) + Utils.getUnsignedInt(lByte));
    }

    public static byte[] calcPortByInt(int port)
    {
        return (new byte[] { ((byte) (port / 256)), ((byte) (port % 256)) });
    }

    public static int getUnsignedInt(byte data)
    {
        return ((data < 0) ? (256 + data) : data);
    }

    public static int indexOf(byte[] toCheck, byte toSearch)
    {
        for(int tempIndex = 0; tempIndex < toCheck.length; tempIndex++)
        {
            if(toCheck[tempIndex] == toSearch)
            {
                return tempIndex;
            }
        }

        return -1;
    }

    public static boolean isTcpPortAvailable(int port)
    {
        //Try to open a tcp-server-socket
        try(ServerSocket serverSocket = new ServerSocket())
        {
            //SetReuseAddress(false) is required for OSX and try to bind port and check for error
            serverSocket.setReuseAddress(false);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLocalHost(), port), 1);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isUdpPortAvailable(int port)
    {
        //Try to open a udp-datagram-socket
        try(DatagramSocket datagramSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getLocalHost(), port)))
        {
            //SetReuseAddress(false) is required for OSX
            datagramSocket.setReuseAddress(false);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
