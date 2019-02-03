package org.coolimc.ProxyServer.SocksProxy;

public enum Socks5Reply
{
    SUCCEEDED(0x00),
    SOCKS_SERVER_FAILURE(0x01),
    CONNECTION_FORBIDDEN_RULESET(0x02),
    NETWORK_UNREACHABLE(0x03),
    HOST_UNREACHABLE(0x04),
    CONNECTION_REFUSED(0x05),
    TTL_EXPIRED(0x06),
    COMMAND_NOT_SUPPORTED(0x07),
    ADDRESS_TYPE_NOT_SUPPORTED(0x08);

    private final int byteCode;

    public static Socks5Reply valueOf(int code)
    {
        if(code == SUCCEEDED.getIntCode())
            return SUCCEEDED;

        if(code == SOCKS_SERVER_FAILURE.getIntCode())
            return SOCKS_SERVER_FAILURE;

        if(code == CONNECTION_FORBIDDEN_RULESET.getIntCode())
            return CONNECTION_FORBIDDEN_RULESET;

        if(code == NETWORK_UNREACHABLE.getIntCode())
            return NETWORK_UNREACHABLE;

        if(code == HOST_UNREACHABLE.getIntCode())
            return HOST_UNREACHABLE;

        if(code == CONNECTION_REFUSED.getIntCode())
            return CONNECTION_REFUSED;

        if(code == TTL_EXPIRED.getIntCode())
            return TTL_EXPIRED;

        if(code == COMMAND_NOT_SUPPORTED.getIntCode())
            return COMMAND_NOT_SUPPORTED;

        if(code == ADDRESS_TYPE_NOT_SUPPORTED.getIntCode())
            return ADDRESS_TYPE_NOT_SUPPORTED;

        return null;
    }

    Socks5Reply(int code)
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
