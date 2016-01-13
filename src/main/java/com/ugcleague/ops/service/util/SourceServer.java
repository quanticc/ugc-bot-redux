package com.ugcleague.ops.service.util;

import com.github.koraktor.steamcondenser.exceptions.SteamCondenserException;

import java.net.InetAddress;

public class SourceServer extends com.github.koraktor.steamcondenser.steam.servers.SourceServer {

    public SourceServer(InetAddress address, Integer port) throws SteamCondenserException {
        super(address, port);
    }

    public SourceServer(InetAddress address) throws SteamCondenserException {
        super(address);
    }

    public SourceServer(String address, Integer port) throws SteamCondenserException {
        super(address, port);
    }

    public SourceServer(String address) throws SteamCondenserException {
        super(address);
    }

    @Override
    public String toString() {
        return this.ipAddress + ":" + this.port;
    }

}
