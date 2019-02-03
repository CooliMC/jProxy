package org.coolimc.ProxyServer.SocksProxy;

public enum SocksCommand
{
    ESTABLISH_TCP_CONNECTION(0x01),
    ESTABLISH_TCP_PORT_SERVER(0x02),
    ESTABLISH_UDP_CONNECTION(0x03);

    private final int byteCode;

    public static SocksCommand valueOf(int code)
    {
        if(code == ESTABLISH_TCP_CONNECTION.getIntCode())
            return ESTABLISH_TCP_CONNECTION;

        if(code == ESTABLISH_TCP_PORT_SERVER.getIntCode())
            return ESTABLISH_TCP_PORT_SERVER;

        if(code == ESTABLISH_UDP_CONNECTION.getIntCode())
            return ESTABLISH_UDP_CONNECTION;

        return null;
    }

    SocksCommand(int code)
    {
        this.byteCode = code;
    }

    public int getIntCode()
    {
        return this.byteCode;
    }

    public byte getByteCode()
    {
        return ((byte) this.byteCode);
    }
}
