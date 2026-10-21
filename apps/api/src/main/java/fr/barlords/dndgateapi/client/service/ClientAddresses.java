package fr.barlords.dndgateapi.client.service;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * The client of a request, for the lockout and the rate limits. It is the remote address of the connection,
 * which the container replaces by the one of the real client behind a trusted proxy
 * (server.forward-headers-strategy=native); the forwarded headers are never read here.
 * <p>
 * An IPv6 client usually owns a whole /64 and can rotate inside it for free, so an IPv6 address stands for its
 * /64 prefix. An IPv4-mapped IPv6 address (::ffff:a.b.c.d) is the IPv4 address it wraps.
 */
public final class ClientAddresses {

    private ClientAddresses() {
    }

    public static ClientAddress of(HttpServletRequest request) {
        return new ClientAddress(normalise(request.getRemoteAddr()));
    }

    private static String withoutZone(String address) {
        int zone = address.indexOf('%');
        return zone < 0 ? address : address.substring(0, zone);
    }

    /** The key under which an address is counted: the address itself for IPv4, the /64 prefix for IPv6. */
    public static String normalise(String remoteAddress) {
        if (remoteAddress == null || !remoteAddress.contains(":")) {
            return remoteAddress;
        }

        InetAddress parsed;
        try {
            // an IPv6 literal: no name resolution happens
            parsed = InetAddress.getByName(withoutZone(remoteAddress));
        } catch (UnknownHostException e) {
            return remoteAddress;
        }

        if (parsed instanceof Inet4Address) {
            // the JDK already turns ::ffff:a.b.c.d into an IPv4 address
            return parsed.getHostAddress();
        }
        if (parsed instanceof Inet6Address) {
            byte[] bytes = parsed.getAddress();
            return String.format("%x:%x:%x:%x::/64",
                    ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff),
                    ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff),
                    ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff),
                    ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff));
        }
        return remoteAddress;
    }

}
