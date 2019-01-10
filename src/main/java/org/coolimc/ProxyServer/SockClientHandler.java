package org.coolimc.ProxyServer;

import java.net.Socket;

public class SockClientHandler
{
    private final Socket sok;

    public SockClientHandler(Socket toManage)
    {
        this.sok = toManage;
    }


}
