package org.tron.core.event;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;
import org.tron.core.services.event.*;
import org.tron.core.services.event.bo.BlockEvent;
import org.tron.core.store.DynamicPropertiesStore;

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

    DynamicPropertiesStore dynamicPropertiesStore = mock(DynamicPropertiesStore.class);
    ChainBaseManager chainBaseManager = mock(ChainBaseManager.class);
    Mockito.when(manager.getDynamicPropertiesStore()).thenReturn(dynamicPropertiesStore);
    Mockito.when(manager.getChainBaseManager()).thenReturn(chainBaseManager);

    BlockCapsule.BlockId b0 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 0);
    BlockEventCache.init(b0);

    BlockEvent be1 = new BlockEvent();
    BlockCapsule.BlockId b1 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    BlockCapsule c1 = new BlockCapsule(1L, b0, 0L, ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be1.setBlockId(b1);
    be1.setParentId(b0);
    BlockEventCache.init(b1);

    BlockEvent be2 = new BlockEvent();
    BlockCapsule.BlockId b2 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    BlockCapsule c2 = new BlockCapsule(2L, b1, 0L, ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be2.setBlockId(b2);
    be2.setParentId(b1);

    BlockEvent be22 = new BlockEvent();
    BlockCapsule.BlockId b22 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    BlockCapsule c22 = new BlockCapsule(2L, b1, 0L, ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be22.setBlockId(b22);
    be22.setParentId(b1);

    BlockEvent be3 = new BlockEvent();
    BlockCapsule.BlockId b3 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    BlockCapsule c3 = new BlockCapsule(3L, b2, 0L, ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be3.setBlockId(b3);
    be3.setParentId(b2);

    BlockEvent be23 = new BlockEvent();
    BlockCapsule.BlockId b23 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    BlockCapsule c23 = new BlockCapsule(3L, b22, 0L, ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be23.setBlockId(b23);
    be23.setParentId(b22);

    Mockito.when(dynamicPropertiesStore.getLatestBlockHeaderNumber()).thenReturn(1l);
    Mockito.when(blockEventGet.getBlockEvent(1L)).thenReturn(be1);
    blockEventLoad.load();
    Assert.assertEquals(be1, BlockEventCache.getHead());
  }
}
