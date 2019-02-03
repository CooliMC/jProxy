package org.coolimc.ProxyServer.Tests;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;

public interface NetworkInterfaceApi extends Library
{
    NetworkInterfaceApi INSTANCE = (NetworkInterfaceApi) Native.load(
        (Platform.isWindows() ? "msvcrt" : "c"),
        (NetworkInterfaceApi.class)
    );

    void printf(String format, Object... args);
}
