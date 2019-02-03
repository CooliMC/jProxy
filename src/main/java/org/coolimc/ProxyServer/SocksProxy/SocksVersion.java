package org.coolimc.ProxyServer.SocksProxy;

public enum SocksVersion
{
    Socks4(0x04),
    Socks5(0x05);

    private final int byteCode;

    public static SocksVersion valueOf(int code)
    {
        if(code == Socks4.getIntCode())
            return Socks4;

        if(code == Socks5.getIntCode())
            return Socks5;

        return null;
    }

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
