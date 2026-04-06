// framework/src/test/java/org/tron/core/services/ratelimiter/cidr/CidrBlockTest.java
package org.tron.core.services.ratelimiter.cidr;

import java.net.InetAddress;
import org.junit.Assert;
import org.junit.Test;

public class CidrBlockTest {

  // ── IPv4 parsing and containment ─────────────────────────────────────────

  @Test
  public void testIpv4Contains() throws Exception {
    CidrBlock cidr = CidrBlock.parse("192.168.1.0/24");
    Assert.assertTrue(cidr.contains(InetAddress.getByName("192.168.1.1")));
    Assert.assertTrue(cidr.contains(InetAddress.getByName("192.168.1.254")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("192.168.2.1")));
  }

  @Test
  public void testIpv4SingleHostExplicit() throws Exception {
    CidrBlock cidr = CidrBlock.parse("10.0.0.1/32");
    Assert.assertTrue(cidr.contains(InetAddress.getByName("10.0.0.1")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("10.0.0.2")));
  }

  @Test
  public void testIpv4SingleHostImplicit() throws Exception {
    // No "/" — should auto-complete to /32
    CidrBlock cidr = CidrBlock.parse("10.0.0.1");
    Assert.assertEquals(32, cidr.prefixLength());
    Assert.assertTrue(cidr.contains(InetAddress.getByName("10.0.0.1")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("10.0.0.2")));
  }

  @Test
  public void testIpv4PrefixLengths() {
    Assert.assertEquals(24, CidrBlock.parse("192.168.1.0/24").prefixLength());
    Assert.assertEquals(8,  CidrBlock.parse("10.0.0.0/8").prefixLength());
    Assert.assertEquals(32, CidrBlock.parse("1.2.3.4/32").prefixLength());
  }

  // ── IPv6 parsing and containment ─────────────────────────────────────────

  @Test
  public void testIpv6Contains() throws Exception {
    CidrBlock cidr = CidrBlock.parse("2001:db8::/32");
    Assert.assertTrue(cidr.contains(InetAddress.getByName("2001:db8::1")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("2001:db9::1")));
  }

  @Test
  public void testIpv6SingleHostExplicit() throws Exception {
    CidrBlock cidr = CidrBlock.parse("::1/128");
    Assert.assertTrue(cidr.contains(InetAddress.getByName("::1")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("::2")));
  }

  @Test
  public void testIpv6SingleHostImplicit() throws Exception {
    // No "/" — should auto-complete to /128
    CidrBlock cidr = CidrBlock.parse("::1");
    Assert.assertEquals(128, cidr.prefixLength());
    Assert.assertTrue(cidr.contains(InetAddress.getByName("::1")));
  }

  // ── IPv4-mapped IPv6 normalization ────────────────────────────────────────

  @Test
  public void testIpv4MappedIpv6MatchesIpv4Cidr() throws Exception {
    // An IPv4 CIDR rule must match clients connecting via a dual-stack IPv6 socket
    // whose address is returned as ::ffff:192.168.1.5 by the JVM.
    CidrBlock cidr = CidrBlock.parse("192.168.1.0/24");
    InetAddress mapped = InetAddress.getByName("::ffff:192.168.1.5");
    Assert.assertTrue(cidr.contains(mapped));
  }

  @Test
  public void testIpv4MappedIpv6ParseNormalized() throws Exception {
    // Parsing "::ffff:10.0.0.1" (IPv4-mapped) should normalize to IPv4 and yield /32
    CidrBlock cidr = CidrBlock.parse("::ffff:10.0.0.1");
    Assert.assertEquals(32, cidr.prefixLength());
    Assert.assertTrue(cidr.contains(InetAddress.getByName("10.0.0.1")));
    Assert.assertFalse(cidr.contains(InetAddress.getByName("10.0.0.2")));
  }

  // ── Error handling ────────────────────────────────────────────────────────

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidAddressThrows() {
    CidrBlock.parse("not-a-cidr");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNegativePrefixThrows() {
    CidrBlock.parse("10.0.0.0/-1");
  }

  @Test(expected = IllegalArgumentException.class)
  public void testTooLargePrefixIpv4Throws() {
    CidrBlock.parse("10.0.0.0/33");
  }

  // ── Cross-family mismatch ─────────────────────────────────────────────────

  @Test
  public void testIpv4DoesNotMatchIpv6Cidr() throws Exception {
    CidrBlock v6cidr = CidrBlock.parse("2001:db8::/32");
    Assert.assertFalse(v6cidr.contains(InetAddress.getByName("192.168.0.1")));
  }
}
