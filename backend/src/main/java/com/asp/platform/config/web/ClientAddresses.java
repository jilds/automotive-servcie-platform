package com.asp.platform.config.web;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/// The key a client is rate-limited by (ADR 0004). An IPv4 address counts as itself. An IPv6 client usually has a
/// whole /64 to itself and can pick a new address in it for each request, so IPv6 counts by its /64 prefix: the first
/// 8 bytes, written in the normalised form [InetAddress] gives.
///
/// The address is the direct peer's (`getRemoteAddr()`). Behind a reverse proxy or load balancer that is the proxy:
/// set `server.forward-headers-strategy` so it becomes the client's, and let only the proxy set those headers.
final class ClientAddresses {

    private static final int IPV6_PREFIX_BYTES = 8;

    private ClientAddresses() {
    }

    static String key(String remoteAddr) {
        String literal = remoteAddr.startsWith("[") && remoteAddr.endsWith("]")
                ? remoteAddr.substring(1, remoteAddr.length() - 1)
                : remoteAddr;
        if (literal.indexOf(':') < 0) {
            return literal;
        }
        int zone = literal.indexOf('%');
        if (zone >= 0) {
            literal = literal.substring(0, zone);
        }
        try {
            // A literal with ':' is parsed, never resolved through DNS.
            InetAddress address = InetAddress.getByName(literal);
            if (!(address instanceof Inet6Address)) {
                return address.getHostAddress();
            }
            byte[] prefix = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(prefix, IPV6_PREFIX_BYTES, 16, (byte) 0);
            return InetAddress.getByAddress(prefix).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return literal;
        }
    }
}
