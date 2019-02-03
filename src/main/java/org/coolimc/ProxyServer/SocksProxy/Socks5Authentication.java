package org.coolimc.ProxyServer.SocksProxy;

public enum Socks5Authentication
{
    NO_AUTHENTICATION_REQUIRED(0x00),
    GSS_API(0x01),
    USERNAME_PASSWORD(0x02),
    NO_ACCEPTABLE_METHODS(0xFF);

    private final int byteCode;

    public static Socks5Authentication valueOf(int code)
    {
        if(code == NO_AUTHENTICATION_REQUIRED.getIntCode())
            return NO_AUTHENTICATION_REQUIRED;

        if(code == GSS_API.getIntCode())
            return GSS_API;

        if(code == USERNAME_PASSWORD.getIntCode())
            return USERNAME_PASSWORD;

        if(code == NO_ACCEPTABLE_METHODS.getIntCode())
            return NO_ACCEPTABLE_METHODS;

        return null;
    }

    Socks5Authentication(int code)
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
