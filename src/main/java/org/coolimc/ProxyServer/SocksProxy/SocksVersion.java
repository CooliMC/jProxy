package org.coolimc.ProxyServer.SocksProxy;

public enum SocksVersion
{
    Socks4(0x04),
    Socks5(0x05);

    private final int byteCode;

    SocksVersion(int code)
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
