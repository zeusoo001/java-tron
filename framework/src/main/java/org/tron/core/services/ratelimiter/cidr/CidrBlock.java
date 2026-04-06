// framework/src/main/java/org/tron/core/services/ratelimiter/cidr/CidrBlock.java
package org.tron.core.services.ratelimiter.cidr;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Immutable, thread-safe CIDR block for IPv4 or IPv6.
 *
 * <p>IPv4-mapped IPv6 addresses (::ffff:x.x.x.x) are normalized to their IPv4 form
 * in both {@link #parse} and {@link #contains}, so that IPv4 CIDR rules match
 * clients connecting via dual-stack sockets.
 */
public final class CidrBlock {

  private final byte[] networkAddress;  // canonical masked network prefix
  private final int    prefixLen;

  private CidrBlock(byte[] networkAddress, int prefixLen) {
    this.networkAddress = networkAddress;
    this.prefixLen      = prefixLen;
  }

  /**
   * Parses a CIDR string. Accepted forms:
   * <ul>
   *   <li>{@code "192.168.1.0/24"} — IPv4 CIDR</li>
   *   <li>{@code "10.0.0.1/32"} or {@code "10.0.0.1"} — single IPv4 host (auto /32)</li>
   *   <li>{@code "2001:db8::/32"} — IPv6 CIDR</li>
   *   <li>{@code "::1/128"} or {@code "::1"} — single IPv6 host (auto /128)</li>
   * </ul>
   *
   * @throws IllegalArgumentException on any parse error
   */
  public static CidrBlock parse(String cidr) {
    if (cidr == null || cidr.isEmpty()) {
      throw new IllegalArgumentException("Invalid CIDR: " + cidr);
    }

    final String  addressPart;
    final Integer explicitPrefix;  // null = no slash (auto-complete to /32 or /128)

    if (cidr.contains("/")) {
      String[] parts = cidr.split("/", 2);
      addressPart = parts[0];
      try {
        explicitPrefix = Integer.parseInt(parts[1]);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid CIDR prefix: " + parts[1], e);
      }
    } else {
      addressPart    = cidr;
      explicitPrefix = null;
    }

    InetAddress addr;
    try {
      addr = InetAddress.getByName(addressPart);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Invalid CIDR address: " + addressPart, e);
    }

    // Normalize IPv4-mapped IPv6 (::ffff:x.x.x.x) to IPv4
    addr = normalize(addr);

    int maxBits = addr.getAddress().length * 8;  // 32 for IPv4, 128 for IPv6
    int prefix  = (explicitPrefix == null) ? maxBits : explicitPrefix;

    if (prefix < 0 || prefix > maxBits) {
      throw new IllegalArgumentException(
          "CIDR prefix " + prefix + " out of range [0," + maxBits + "]");
    }

    return new CidrBlock(mask(addr.getAddress(), prefix), prefix);
  }

  /** Returns true if {@code ip} belongs to this CIDR block. */
  public boolean contains(InetAddress ip) {
    ip = normalize(ip);
    byte[] addr = ip.getAddress();
    if (addr.length != networkAddress.length) {
      return false;  // IPv4 vs IPv6 mismatch
    }
    return Arrays.equals(mask(addr, prefixLen), networkAddress);
  }

  /** The prefix length (e.g. 24 for /24). Larger = more specific. */
  public int prefixLength() {
    return prefixLen;
  }

  @Override
  public String toString() {
    try {
      return InetAddress.getByAddress(networkAddress).getHostAddress() + "/" + prefixLen;
    } catch (UnknownHostException e) {
      return "<invalid>/" + prefixLen;
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

  /**
   * Converts an IPv4-mapped IPv6 address (::ffff:a.b.c.d) to its IPv4 form.
   * All other addresses are returned unchanged.
   */
  static InetAddress normalize(InetAddress addr) {
    if (!(addr instanceof Inet6Address)) {
      return addr;
    }
    byte[] bytes = addr.getAddress();
    // IPv4-mapped pattern: 10 zero bytes + 0xFF 0xFF + 4 IPv4 bytes
    if (bytes.length == 16
        && isZero(bytes, 0, 10)
        && bytes[10] == (byte) 0xFF
        && bytes[11] == (byte) 0xFF) {
      try {
        return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
      } catch (UnknownHostException e) {
        return addr;  // 4-byte array always valid; should never happen
      }
    }
    return addr;
  }

  private static boolean isZero(byte[] arr, int from, int len) {
    for (int i = from; i < from + len; i++) {
      if (arr[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private static byte[] mask(byte[] addr, int prefixLen) {
    byte[] result = Arrays.copyOf(addr, addr.length);
    for (int i = 0; i < result.length; i++) {
      int bitsRemaining = prefixLen - i * 8;
      if (bitsRemaining <= 0) {
        result[i] = 0;
      } else if (bitsRemaining < 8) {
        result[i] &= (byte) (0xFF << (8 - bitsRemaining));
      }
      // else: full byte — no masking needed
    }
    return result;
  }
}
