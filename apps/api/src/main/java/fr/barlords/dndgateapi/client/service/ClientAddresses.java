package fr.barlords.dndgateapi.client.service;

import fr.barlords.dndgateapi.domain.functional.model.account.ClientAddress;
import jakarta.servlet.http.HttpServletRequest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * The client of a request, for the lockout and the rate limits. It is the remote address of the connection,
 * which the container replaces by the one of the real client behind a trusted proxy
 * (server.forward-headers-strategy=native); the forwarded headers are never read here.
 * <p>
 * An IPv6 client usually owns a whole /64 and can rotate inside it for free, so an IPv6 address stands for its
 * /64 prefix. An IPv4-mapped IPv6 address (::ffff:a.b.c.d) is the IPv4 address it wraps.
 */
public final class ClientAddresses {

    /** One shared bucket for everything that is not an address, so that junk values cannot mint fresh counters. */
    static final String INVALID = "invalid";

    private static final Pattern IPV4 = Pattern.compile("[0-9]{1,3}(\\.[0-9]{1,3}){3}");

    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:.]{2,45}");

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
        if (remoteAddress == null) {
            return INVALID;
        }
        String address = withoutZone(remoteAddress);
        if (!address.contains(":")) {
            return IPV4.matcher(address).matches() ? address : INVALID;
        }
        // Only hex digits, colons and dots reach the parser: it resolves anything that looks like a host name
        if (!IPV6_CHARACTERS.matcher(address).matches()) {
            return INVALID;
        }

        InetAddress parsed;
        try {
            parsed = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return INVALID;
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
        return INVALID;
    }

}
