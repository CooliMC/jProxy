package org.coolimc.ProxyServer.ProxyUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

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

    public static int calcPort(byte hByte, byte lByte)
    {
        return ((Utils.getUnsignedInt(hByte) * 256) + Utils.getUnsignedInt(lByte));
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
}
