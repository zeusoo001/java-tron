// framework/src/test/java/org/tron/core/services/ratelimiter/cidr/CidrRateLimiterTest.java
package org.tron.core.services.ratelimiter.cidr;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.tron.common.parameter.CidrRuleConfig;
import org.tron.core.services.ratelimiter.RuntimeData;

public class CidrRateLimiterTest {

  // Helper: create RuntimeData with a given IP string (mirrors GlobalRateLimiterTest pattern)
  private static RuntimeData rd(String ip) throws Exception {
    RuntimeData rd = new RuntimeData(null);
    Field f = rd.getClass().getDeclaredField("address");
    f.setAccessible(true);
    f.set(rd, ip == null ? "" : ip);
    return rd;
  }

  // ── Optional semantics ────────────────────────────────────────────────────

  @Test
  public void testNoRuleMatchReturnsEmpty() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(Collections.emptyList());
    Assert.assertFalse(rl.tryAcquire(rd("10.0.0.1")).isPresent());
  }

  @Test
  public void testMatchReturnsPresent() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("10.0.0.0/24", 1000.0)));
    Optional<Boolean> result = rl.tryAcquire(rd("10.0.0.5"));
    Assert.assertTrue(result.isPresent());
    Assert.assertTrue(result.get());  // qps=1000: first token always available
  }

  @Test
  public void testMatchBlocksWhenBucketExhausted() throws Exception {
    // qps=1 means only one token; a second request from the same rule is blocked
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("10.0.0.0/24", 1.0)));
    Assert.assertEquals(Optional.of(true),  rl.tryAcquire(rd("10.0.0.1")));
    Assert.assertEquals(Optional.of(false), rl.tryAcquire(rd("10.0.0.2")));  // same bucket
  }

  // ── Aggregate bucket (all IPs in CIDR share one bucket) ──────────────────

  @Test
  public void testSharedBucketAcrossIpsInSameCidr() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("10.0.0.0/24", 1.0)));
    Assert.assertEquals(Optional.of(true),  rl.tryAcquire(rd("10.0.0.1")));
    // Different IP in the same /24 — same bucket, now exhausted
    Assert.assertEquals(Optional.of(false), rl.tryAcquire(rd("10.0.0.99")));
  }

  // ── Most-specific-first matching ─────────────────────────────────────────

  @Test
  public void testMostSpecificCidrWins() throws Exception {
    // /32 is more specific than /24; 10.0.0.1 must hit the /32 rule (qps=1)
    CidrRateLimiter rl = new CidrRateLimiter(Arrays.asList(
        new CidrRuleConfig("10.0.0.0/24", 1000.0),
        new CidrRuleConfig("10.0.0.1/32",    1.0)));

    Assert.assertEquals(Optional.of(true),  rl.tryAcquire(rd("10.0.0.1")));  // /32, qps=1
    Assert.assertEquals(Optional.of(false), rl.tryAcquire(rd("10.0.0.1")));  // /32 exhausted

    // 10.0.0.2 hits /24 (qps=1000), completely independent bucket
    Assert.assertEquals(Optional.of(true), rl.tryAcquire(rd("10.0.0.2")));
  }

  // ── IPv6 ─────────────────────────────────────────────────────────────────

  @Test
  public void testIpv6CidrMatch() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("2001:db8::/32", 1.0)));
    Assert.assertEquals(Optional.of(true),  rl.tryAcquire(rd("2001:db8::1")));
    Assert.assertEquals(Optional.of(false), rl.tryAcquire(rd("2001:db8::2")));
    Assert.assertFalse(rl.tryAcquire(rd("2001:db9::1")).isPresent());  // no match
  }

  // ── IPv4-mapped IPv6 normalization ────────────────────────────────────────

  @Test
  public void testIpv4MappedIpv6MatchesIpv4Cidr() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("192.168.1.0/24", 1.0)));
    // ::ffff:192.168.1.5 normalizes to IPv4 192.168.1.5 — must match the /24 rule
    Assert.assertEquals(Optional.of(true), rl.tryAcquire(rd("::ffff:192.168.1.5")));
  }

  // ── Empty / unparseable IP ────────────────────────────────────────────────

  @Test
  public void testEmptyIpReturnsEmpty() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", 1.0)));
    Assert.assertFalse(rl.tryAcquire(rd("")).isPresent());
  }

  @Test
  public void testNullIpReturnsEmpty() throws Exception {
    CidrRateLimiter rl = new CidrRateLimiter(
        Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", 1.0)));
    Assert.assertFalse(rl.tryAcquire(rd(null)).isPresent());
  }

  // ── Constructor validation (TronError on startup) ─────────────────────────

  @Test(expected = org.tron.core.exception.TronError.class)
  public void testInvalidCidrThrowsTronError() {
    new CidrRateLimiter(Collections.singletonList(new CidrRuleConfig("not-a-cidr", 100.0)));
  }

  @Test(expected = org.tron.core.exception.TronError.class)
  public void testZeroQpsThrowsTronError() {
    new CidrRateLimiter(Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", 0.0)));
  }

  @Test(expected = org.tron.core.exception.TronError.class)
  public void testNegativeQpsThrowsTronError() {
    new CidrRateLimiter(Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", -1.0)));
  }

  // ── Duplicate prefix length (WARN, no throw) ─────────────────────────────

  @Test
  public void testDuplicatePrefixLengthDoesNotThrow() throws Exception {
    // Two different /24 rules — constructor should log WARN but not throw
    List<CidrRuleConfig> rules = Arrays.asList(
        new CidrRuleConfig("10.0.0.0/24", 1000.0),
        new CidrRuleConfig("10.0.1.0/24", 1000.0));
    CidrRateLimiter rl = new CidrRateLimiter(rules);  // must not throw
    Assert.assertFalse(rl.tryAcquire(rd("10.0.2.1")).isPresent());  // no match → empty
  }
}
