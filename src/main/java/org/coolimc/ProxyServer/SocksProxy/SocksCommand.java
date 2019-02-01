package org.coolimc.ProxyServer.SocksProxy;

public enum SocksCommand
{
    ESTABLISH_TCP_CONNECTION(0x01),
    ESTABLISH_TCP_PORT_SERVER(0x02),
    ESTABLISH_UDP_CONNECTION(0x03);

    private final int byteCode;

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
