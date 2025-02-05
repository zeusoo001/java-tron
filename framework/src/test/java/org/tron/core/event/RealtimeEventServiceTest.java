package org.tron.core.event;

import com.google.protobuf.ByteString;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.tron.common.logsfilter.capsule.BlockLogTriggerCapsule;
import org.tron.common.logsfilter.capsule.TransactionLogTriggerCapsule;
import org.tron.common.logsfilter.capsule.TriggerCapsule;
import org.tron.common.logsfilter.trigger.ContractEventTrigger;
import org.tron.common.logsfilter.trigger.ContractLogTrigger;
import org.tron.common.utils.ReflectUtils;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;
import org.tron.core.services.event.*;
import org.tron.core.services.event.BlockEvent;
import org.tron.core.services.event.SmartContractTrigger;
import org.tron.core.services.event.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static org.mockito.Mockito.mock;

public class RealtimeEventServiceTest {

  RealtimeEventService realtimeEventService = new RealtimeEventService();

  @Test
  public void test() throws Exception {
    BlockEvent be1 = new BlockEvent();
    BlockCapsule.BlockId b1 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
    be1.setBlockId(b1);
    be1.setParentId(b1);
    BlockEventCache.init(b1);

    BlockEvent be2 = new BlockEvent();
    BlockCapsule.BlockId b2 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 2);
    be2.setBlockId(b2);
    be2.setParentId(b1);
    BlockEventCache.add(be2);
    Assert.assertEquals(be2, BlockEventCache.getHead());
    Assert.assertEquals(be2, BlockEventCache.getBlockEvent(b2));

    Event event = new Event(be2, true);

    realtimeEventService.add(event);
    realtimeEventService.work();

    BlockingQueue<TriggerCapsule> queue = new BlockingArrayQueue<>();
    Manager manager = mock(Manager.class);
    Mockito.when(manager.getTriggerCapsuleQueue()).thenReturn(queue);
    ReflectUtils.setFieldValue(realtimeEventService, "manager", manager);

    BlockCapsule blockCapsule = new BlockCapsule(0l, Sha256Hash.ZERO_HASH, 0l,
        ByteString.copyFrom(BlockEventCacheTest.getBlockId()));
    be2.setBlockLogTriggerCapsule(new BlockLogTriggerCapsule(blockCapsule));
    ReflectUtils.setFieldValue(realtimeEventService, "blockLogTriggerEnable", true);
    ReflectUtils.setFieldValue(realtimeEventService, "blockLogTriggerSolidified", false);

    realtimeEventService.add(event);
    realtimeEventService.work();

    Assert.assertEquals(1, queue.size());

    be2.setBlockLogTriggerCapsule(null);
    queue.poll();

    List<TransactionLogTriggerCapsule> list = new ArrayList<>();
    list.add(mock(TransactionLogTriggerCapsule.class));
    be2.setTransactionLogTriggerCapsules(list);

    ReflectUtils.setFieldValue(realtimeEventService, "transactionLogTriggerEnable", true);
    ReflectUtils.setFieldValue(realtimeEventService, "transactionLogTriggerSolidified", false);
    realtimeEventService.flush(be2, event.isRemove());
    Assert.assertEquals(1, queue.size());

    be2.setTransactionLogTriggerCapsules(null);

    SmartContractTrigger contractTrigger = new SmartContractTrigger();
    be2.setSmartContractTrigger(contractTrigger);

    contractTrigger.getContractEventTriggers().add(mock(ContractEventTrigger.class));
    ReflectUtils.setFieldValue(realtimeEventService, "contractLogTriggerEnable", true);
    try {
      realtimeEventService.flush(be2, event.isRemove());
    } catch (Exception e) {
      Assert.assertTrue(e instanceof NullPointerException);
    }

    contractTrigger.getContractEventTriggers().clear();

    realtimeEventService.flush(be2, event.isRemove());

    contractTrigger.getContractLogTriggers().add(mock(ContractLogTrigger.class));
    ReflectUtils.setFieldValue(realtimeEventService, "contractEventTriggerEnable", true);

    try {
      realtimeEventService.flush(be2, event.isRemove());
    } catch (Exception e) {
      Assert.assertTrue(e instanceof NullPointerException);
    }
  }
}
