package fr.barlords.dndgateapi.client.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ClientAddressesTest {

    @Test
    void an_ipv4_address_is_kept_as_it_is() {
        Assertions.assertEquals("203.0.113.7", ClientAddresses.normalise("203.0.113.7"));
    }

    @Test
    void an_ipv4_mapped_ipv6_address_is_the_ipv4_address() {
        Assertions.assertEquals("203.0.113.7", ClientAddresses.normalise("::ffff:203.0.113.7"));
        Assertions.assertEquals("203.0.113.7", ClientAddresses.normalise("0:0:0:0:0:ffff:cb00:7107"));
    }

    @Test
    void two_ipv6_addresses_of_the_same_64_share_one_key() {
        Assertions.assertEquals(
                ClientAddresses.normalise("2001:db8:1:2:aaaa:bbbb:cccc:dddd"),
                ClientAddresses.normalise("2001:0db8:0001:0002:1111:2222:3333:4444"));
        Assertions.assertEquals("2001:db8:1:2::/64", ClientAddresses.normalise("2001:db8:1:2:aaaa:bbbb:cccc:dddd"));
    }

    @Test
    void ipv6_addresses_of_different_64_have_different_keys() {
        Assertions.assertNotEquals(
                ClientAddresses.normalise("2001:db8:1:2::1"),
                ClientAddresses.normalise("2001:db8:1:3::1"));
    }

    @Test
    void the_loopback_and_compressed_forms_are_handled() {
        Assertions.assertEquals("0:0:0:0::/64", ClientAddresses.normalise("0:0:0:0:0:0:0:1"));
        Assertions.assertEquals("0:0:0:0::/64", ClientAddresses.normalise("::1"));
        Assertions.assertEquals("fe80:0:0:0::/64", ClientAddresses.normalise("fe80::1%eth0"));
    }

    @Test
    void something_that_is_not_an_address_is_left_alone() {
        Assertions.assertEquals("not:an:address:at:all:zz", ClientAddresses.normalise("not:an:address:at:all:zz"));
        Assertions.assertNull(ClientAddresses.normalise(null));
    }

}
