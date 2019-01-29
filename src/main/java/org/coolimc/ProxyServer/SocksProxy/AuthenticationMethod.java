package org.coolimc.ProxyServer.SocksProxy;

public enum AuthenticationMethod
{
    NO_AUTHENTICATION_REQUIRED(0x00),
    GSS_API(0x01),
    USERNAME_PASSWORD(0x02),
    NO_ACCEPTABLE_METHODS(0xFF);

    private final int byteCode;

    AuthenticationMethod(int code)
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
