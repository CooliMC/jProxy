import org.coolimc.ProxyServer.HttpProxy.HttpProxyServer;
import org.coolimc.ProxyServer.SocksProxy.SocksProxyServer;

import org.junit.jupiter.api.Test;

public class TestServerSocket
{
    @Test
    void testHttpProxyStart()
    {
        //Test the start of the HttpServer
        new HttpProxyServer(0).listen();
    }

    @Test
    void testSocksProxyStart()
    {
        //Test the start of the SocksServer
        new SocksProxyServer(0).listen();
    }
}
