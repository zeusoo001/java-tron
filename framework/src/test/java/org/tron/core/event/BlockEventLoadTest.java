package org.tron.core.event;

import org.junit.Test;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;
import org.tron.core.services.event.*;

import static org.mockito.Mockito.mock;

public class BlockEventLoadTest {
  BlockEventLoad blockEventLoad = new BlockEventLoad();

  @Test
  public void test() throws Exception {
    RealtimeEventService realtimeEventService = new RealtimeEventService();
    BlockEventGet blockEventGet = mock(BlockEventGet.class);
    Manager manager = mock(Manager.class);
    ReflectUtils.setFieldValue(blockEventLoad, "realtimeEventService", realtimeEventService);
    ReflectUtils.setFieldValue(blockEventLoad, "blockEventGet", blockEventGet);
    ReflectUtils.setFieldValue(blockEventLoad, "manager", manager);

    BlockCapsule.BlockId b0 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 0);
    BlockEventCache.init(b0);


    BlockEvent be1 = new BlockEvent();
    BlockCapsule.BlockId b1 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be1.setBlockId(b1);
    be1.setParentId(b0);
    BlockEventCache.init(b1);

    BlockEvent be2 = new BlockEvent();
    BlockCapsule.BlockId b2 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be2.setBlockId(b2);
    be2.setParentId(b1);

    BlockEvent be22 = new BlockEvent();
    BlockCapsule.BlockId b22 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be22.setBlockId(b22);
    be22.setParentId(b1);

    BlockEvent be3 = new BlockEvent();
    BlockCapsule.BlockId b3 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be3.setBlockId(b3);
    be3.setParentId(b2);

    BlockEvent be23 = new BlockEvent();
    BlockCapsule.BlockId b23 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be23.setBlockId(b23);
    be23.setParentId(b22);

  }
}
