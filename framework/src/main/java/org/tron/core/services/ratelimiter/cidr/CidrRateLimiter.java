// framework/src/main/java/org/tron/core/services/ratelimiter/cidr/CidrRateLimiter.java
package org.tron.core.services.ratelimiter.cidr;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.RateLimiter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.parameter.CidrRuleConfig;
import org.tron.core.config.args.Args;
import org.tron.core.exception.TronError;
import org.tron.core.exception.TronError.ErrCode;
import org.tron.core.services.ratelimiter.RuntimeData;

/**
 * Matches incoming IP addresses against an ordered list of CIDR rules
 * (most-specific prefix first) and applies an aggregate Guava {@link RateLimiter}
 * per rule.
 *
 * <p>The static {@link #instance} is initialized at class-load time from {@link Args}
 * and is used by {@link org.tron.core.services.http.RateLimiterServlet} and
 * {@link org.tron.core.services.ratelimiter.RateLimiterInterceptor}.
 * Tests may replace {@code instance} via reflection.
 *
 * <p>All public methods are thread-safe.
 */
@Slf4j
public class CidrRateLimiter {

  // Package-private so tests can replace it via reflection.
  static CidrRateLimiter instance = new CidrRateLimiter(
      Args.getInstance().getRateLimiterCidrRules());

  // ── Instance state ────────────────────────────────────────────────────────

  private static final class CompiledRule {
    final CidrBlock   block;
    final RateLimiter limiter;

    CompiledRule(CidrBlock block, double qps) {
      this.block   = block;
      this.limiter = RateLimiter.create(qps);
    }
  }

  private final List<CompiledRule> rules;  // sorted: longest prefix first

  /**
   * Constructs a {@code CidrRateLimiter} from the given rule configs.
   * Rules are sorted most-specific-first (descending prefix length).
   *
   * @throws TronError with {@link ErrCode#RATE_LIMITER_INIT} if any rule has an
   *                   invalid CIDR string or {@code qps <= 0}. This causes node
   *                   startup to terminate.
   */
  public CidrRateLimiter(List<CidrRuleConfig> configs) {
    List<CompiledRule> compiled = new ArrayList<>(configs.size());
    for (CidrRuleConfig cfg : configs) {
      if (cfg.getQps() <= 0) {
        throw new TronError(
            "CIDR rate limiter: qps must be > 0, got " + cfg.getQps()
                + " for rule '" + cfg.getCidr() + "'",
            ErrCode.RATE_LIMITER_INIT);
      }
      CidrBlock block;
      try {
        block = CidrBlock.parse(cfg.getCidr());
      } catch (IllegalArgumentException e) {
        throw new TronError(
            "CIDR rate limiter: invalid CIDR '" + cfg.getCidr() + "'",
            e, ErrCode.RATE_LIMITER_INIT);
      }
      compiled.add(new CompiledRule(block, cfg.getQps()));
    }

    // Sort most-specific (longest prefix) first so /32 is checked before /24
    compiled.sort(Comparator.comparingInt(r -> -r.block.prefixLength()));

    // Warn if adjacent rules share the same prefix length (first one in sort order wins)
    for (int i = 0; i < compiled.size() - 1; i++) {
      if (compiled.get(i).block.prefixLength() == compiled.get(i + 1).block.prefixLength()) {
        logger.warn("CidrRateLimiter: duplicate prefix length /{} — "
            + "only the first matching rule applies. Ambiguous rules: {} and {}",
            compiled.get(i).block.prefixLength(),
            compiled.get(i).block,
            compiled.get(i + 1).block);
      }
    }

    this.rules = compiled;
  }

  // ── Public instance API ───────────────────────────────────────────────────

  /**
   * Tests the remote IP from {@code runtimeData} against configured CIDR rules.
   *
   * @return {@link Optional#empty()} — no rule matched; caller proceeds to
   *         {@link org.tron.core.services.ratelimiter.GlobalRateLimiter} normally.<br>
   *         {@link Optional#of(true)} — rule matched, token acquired, request allowed.<br>
   *         {@link Optional#of(false)} — rule matched, rate exceeded, request must be rejected.
   */
  public Optional<Boolean> tryAcquire(RuntimeData runtimeData) {
    String ipStr = runtimeData.getRemoteAddr();
    if (Strings.isNullOrEmpty(ipStr)) {
      return Optional.empty();
    }
    InetAddress addr;
    try {
      addr = InetAddress.getByName(ipStr);
    } catch (UnknownHostException e) {
      logger.debug("CidrRateLimiter: cannot parse IP '{}', skipping CIDR check", ipStr);
      return Optional.empty();
    }

    for (CompiledRule rule : rules) {
      if (rule.block.contains(addr)) {
        return Optional.of(rule.limiter.tryAcquire());
      }
    }

    return Optional.empty();  // no rule matched
  }

  // ── Static façade (called by Servlet and Interceptor) ────────────────────

  /**
   * Delegates to {@link #instance}.
   * Tests replace {@code instance} via reflection to inject rules.
   */
  public static Optional<Boolean> tryAcquireStatic(RuntimeData runtimeData) {
    return instance.tryAcquire(runtimeData);
  }
}
