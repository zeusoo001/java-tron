package org.tron.core.services.ratelimiter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.common.TestConstants;
import org.tron.common.parameter.CidrRuleConfig;
import org.tron.core.config.args.Args;
import org.tron.core.services.ratelimiter.cidr.CidrRateLimiter;

public class GlobalRateLimiterTest {

  /**
   * Reset GlobalRateLimiter's static state to known rates before each test.
   * Static fields are initialized at class-load time from Args, so we must
   * override them via reflection to guarantee test isolation.
   */
  @Before
  public void setUp() throws Exception {
    String[] a = new String[0];
    Args.setParam(a, TestConstants.TEST_CONF);
    resetGlobalRateLimiter(2.0, 1.0);
    setCidrInstance(Collections.emptyList());  // ensure no CIDR rules bleed between tests
  }

  private static void resetGlobalRateLimiter(double globalQps, double ipQps) throws Exception {
    // Reset per-IP QPS value
    Field ipQpsField = GlobalRateLimiter.class.getDeclaredField("IP_QPS");
    ipQpsField.setAccessible(true);
    ipQpsField.set(null, ipQps);

    // Recreate global rate limiter with desired QPS and pre-warm it.
    // Guava's SmoothBursty starts with storedPermits=0, so the first call
    // pre-bills the next slot (advancing nextFreeTicketMicros into the future)
    // and only ONE immediate call succeeds. Setting storedPermits=1.0 gives the
    // limiter exactly one banked token: the first call consumes it (no advance),
    // the second call still passes via pre-billing, and the third call fails.
    // This means exactly floor(qps) = 2 consecutive calls succeed for qps=2.
    RateLimiter rl = RateLimiter.create(globalQps);
    Field storedPermitsField = rl.getClass().getSuperclass()
        .getDeclaredField("storedPermits");
    storedPermitsField.setAccessible(true);
    storedPermitsField.set(rl, 1.0);

    Field rateLimiterField = GlobalRateLimiter.class.getDeclaredField("rateLimiter");
    rateLimiterField.setAccessible(true);
    rateLimiterField.set(null, rl);

    // Clear the per-IP cache so each test starts fresh
    Field cacheField = GlobalRateLimiter.class.getDeclaredField("cache");
    cacheField.setAccessible(true);
    Cache<String, RateLimiter> freshCache = CacheBuilder.newBuilder()
        .maximumSize(10000).expireAfterWrite(1, TimeUnit.HOURS).build();
    cacheField.set(null, freshCache);
  }

  private static RuntimeData runtimeDataFor(String ip) throws Exception {
    RuntimeData runtimeData = new RuntimeData(null);
    Field field = runtimeData.getClass().getDeclaredField("address");
    field.setAccessible(true);
    field.set(runtimeData, ip == null ? "" : ip);
    return runtimeData;
  }

  /**
   * Normal request: passes both IP and global limits.
   */
  @Test
  public void testNormalRequestPasses() throws Exception {
    RuntimeData runtimeData = runtimeDataFor("10.0.0.1");
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(runtimeData));
  }

  /**
   * IP limit exhausted: second request from same IP is rejected without
   * consuming a global token. A third request from a different IP must still
   * pass because the global budget was not wasted.
   * globalQps=2, ipQps=1
   */
  @Test
  public void testIpLimitDoesNotWasteGlobalToken() throws Exception {
    RuntimeData ip1 = runtimeDataFor("10.0.0.1");
    RuntimeData ip2 = runtimeDataFor("10.0.0.2");

    // First request from 10.0.0.1: IP passes (1/1), global passes (1/2)
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(ip1));

    // Second request from 10.0.0.1: IP exhausted → rejected, global NOT consumed
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(ip1));

    // First request from 10.0.0.2: IP passes (1/1), global passes (2/2)
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(ip2));

    // Any further request: global exhausted
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(runtimeDataFor("10.0.0.3")));
  }

  /**
   * Multiple IPs each consume one global token and then hit their own IP limit.
   * globalQps=2, ipQps=1: exactly 2 distinct IPs can succeed.
   */
  @Test
  public void testGlobalCapAcrossMultipleIps() throws Exception {
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(runtimeDataFor("1.1.1.1")));
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(runtimeDataFor("1.1.1.2")));

    // Global budget exhausted; a fresh IP is also rejected
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(runtimeDataFor("1.1.1.3")));
  }

  /**
   * Request with no IP address bypasses the IP-level check and goes straight
   * to the global limiter.
   * globalQps=2: two no-IP requests succeed, third fails.
   */
  @Test
  public void testNoIpAddressFallsBackToGlobalOnly() throws Exception {
    RuntimeData noIp = runtimeDataFor("");

    Assert.assertTrue(GlobalRateLimiter.tryAcquire(noIp));
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(noIp));
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(noIp));
  }

  /**
   * Per-IP limit is independent between different IPs.
   * globalQps=10 (high), ipQps=1: each IP gets exactly one successful request.
   */
  @Test
  public void testPerIpLimitsAreIndependent() throws Exception {
    resetGlobalRateLimiter(10.0, 1.0);

    Assert.assertTrue(GlobalRateLimiter.tryAcquire(runtimeDataFor("2.2.2.1")));
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(runtimeDataFor("2.2.2.1")));

    Assert.assertTrue(GlobalRateLimiter.tryAcquire(runtimeDataFor("2.2.2.2")));
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(runtimeDataFor("2.2.2.2")));
  }

  // ── CIDR integration tests ────────────────────────────────────────────────

  /**
   * CIDR rule blocks an IP. tryAcquireStatic returns Optional.of(false).
   * GlobalRateLimiter must NOT be consulted (global budget preserved).
   */
  @Test
  public void testCidrBlocksBeforeGlobal() throws Exception {
    resetGlobalRateLimiter(10000.0, 10000.0);
    setCidrInstance(Collections.singletonList(new CidrRuleConfig("192.168.1.0/24", 1.0)));

    RuntimeData first  = runtimeDataFor("192.168.1.10");
    RuntimeData second = runtimeDataFor("192.168.1.20");

    // First request consumes the /24 bucket's single token
    Assert.assertEquals(Optional.of(true),  CidrRateLimiter.tryAcquireStatic(first));
    // Second request — same rule, bucket exhausted
    Assert.assertEquals(Optional.of(false), CidrRateLimiter.tryAcquireStatic(second));
  }

  /**
   * IP outside all CIDR rules returns Optional.empty() → GlobalRateLimiter handles it.
   */
  @Test
  public void testNoCidrMatchFallsBackToGlobal() throws Exception {
    resetGlobalRateLimiter(1.0, 10000.0);  // global qps=1
    setCidrInstance(Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", 1000.0)));

    RuntimeData outside = runtimeDataFor("172.16.0.1");
    // CIDR: no match → empty
    Assert.assertFalse(CidrRateLimiter.tryAcquireStatic(outside).isPresent());
    // GlobalRateLimiter: first call passes, second exhausted
    Assert.assertTrue(GlobalRateLimiter.tryAcquire(outside));
    Assert.assertFalse(GlobalRateLimiter.tryAcquire(outside));
  }

  /**
   * IPv4-mapped IPv6 (::ffff:x.x.x.x) is normalized to IPv4 and matches an IPv4 CIDR rule.
   */
  @Test
  public void testIpv4MappedIpv6MatchesCidrRule() throws Exception {
    setCidrInstance(Collections.singletonList(new CidrRuleConfig("10.0.0.0/8", 1.0)));
    RuntimeData mapped = runtimeDataFor("::ffff:10.0.0.5");
    Optional<Boolean> result = CidrRateLimiter.tryAcquireStatic(mapped);
    Assert.assertTrue(result.isPresent());
    Assert.assertTrue(result.get());  // first token
  }

  /**
   * Most-specific CIDR rule wins: /32 is checked before /24.
   */
  @Test
  public void testMostSpecificCidrWinsInIntegration() throws Exception {
    setCidrInstance(Arrays.asList(
        new CidrRuleConfig("10.0.0.0/24", 1000.0),
        new CidrRuleConfig("10.0.0.1/32",    1.0)));

    RuntimeData specific = runtimeDataFor("10.0.0.1");
    Assert.assertEquals(Optional.of(true),  CidrRateLimiter.tryAcquireStatic(specific));
    Assert.assertEquals(Optional.of(false), CidrRateLimiter.tryAcquireStatic(specific));

    // 10.0.0.2 hits /24 (qps=1000) — still passes
    RuntimeData other = runtimeDataFor("10.0.0.2");
    Assert.assertEquals(Optional.of(true), CidrRateLimiter.tryAcquireStatic(other));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static void setCidrInstance(List<CidrRuleConfig> rules) throws Exception {
    Field f = CidrRateLimiter.class.getDeclaredField("instance");
    f.setAccessible(true);
    f.set(null, new CidrRateLimiter(rules));
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
  }
}
