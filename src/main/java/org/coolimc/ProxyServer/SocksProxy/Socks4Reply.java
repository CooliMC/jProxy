package org.coolimc.ProxyServer.SocksProxy;

public enum Socks4Reply
{
    REQUEST_GRANTED(0x5A),
    REQUEST_REJECTED_OR_FAILED(0x5B),
    REQUEST_FAILED_RUNNING_CLIENTID(0x5C),
    REQUEST_FAILED_VERFYING_CLIENTID(0x5D);

    private final int byteCode;

    Socks4Reply(int code)
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
