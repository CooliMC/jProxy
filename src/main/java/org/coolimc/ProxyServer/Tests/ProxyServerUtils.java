package org.coolimc.ProxyServer.Tests;

import org.coolimc.ProxyServer.Tests.NetworkInterfaceApi;

public class ProxyServerUtils
{
    private ProxyServerUtils()
    {

    }

    public static void main(String[] args) {
        NetworkInterfaceApi.INSTANCE.printf("Hello, World\n");
    }
}
