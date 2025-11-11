package org.tron.core.services.ratelimiter;

import java.lang.reflect.Field;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.tron.core.Constant;
import org.tron.core.config.args.Args;

public class GlobalRateLimiterTest {

  @Test
  public void testAcquire() throws Exception {
    String[] a = new String[0];
    Args.setParam(a, Constant.TESTNET_CONF);

    Args.getInstance().setRateLimiterGlobalQps(2);
    Args.getInstance().setRateLimiterGlobalIpQps(1);

    RuntimeData runtimeData = new RuntimeData(null);
    Field field =  runtimeData.getClass().getDeclaredField("address");
    field.setAccessible(true);
    field.set(runtimeData, "127.0.0.1");
    Assert.assertEquals("127.0.0.1", runtimeData.getRemoteAddr());

    boolean flag = GlobalRateLimiter.tryAcquire(runtimeData);
    Assert.assertTrue(flag);

    flag = GlobalRateLimiter.tryAcquire(runtimeData);
    Assert.assertFalse(flag);

    field.set(runtimeData, "127.0.0.2");
    Assert.assertEquals("127.0.0.2", runtimeData.getRemoteAddr());

    flag = GlobalRateLimiter.tryAcquire(runtimeData);
    Assert.assertFalse(flag);
  }

  @AfterClass
  public static void destroy() {
    Args.clearParam();
  }
}