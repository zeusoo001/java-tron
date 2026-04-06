// common/src/main/java/org/tron/common/parameter/CidrRuleConfig.java
package org.tron.common.parameter;

import lombok.Getter;

/**
 * One entry from the {@code rate.limiter.cidr} HOCON list.
 * Example: {@code { cidr = "192.168.1.0/24", qps = 100 }}
 */
@Getter
public class CidrRuleConfig {

  private final String cidr;
  private final double qps;

  public CidrRuleConfig(String cidr, double qps) {
    this.cidr = cidr;
    this.qps  = qps;
  }
}
