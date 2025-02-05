package org.tron.core.event;

import org.eclipse.jetty.util.BlockingArrayQueue;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.tron.common.logsfilter.capsule.BlockLogTriggerCapsule;
import org.tron.common.logsfilter.capsule.TriggerCapsule;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;
import org.tron.core.services.event.*;

import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;

import static org.mockito.Mockito.mock;

public class HistoryEventServiceTest {

  HistoryEventService historyEventService = new HistoryEventService();

//  @Test
//  public void test() throws Exception {
//    historyEventService.init();
//    ReflectUtils.setFieldValue(historyEventService, "startSyncBlockNum", 100);
//    historyEventService.init();
//
//    BlockEvent be1 = new BlockEvent();
//    BlockCapsule.BlockId b1 = new BlockCapsule.BlockId(BlockEventCacheTest.getBlockId(), 1);
//    be1.setBlockId(b1);
//    be1.setParentId(b1);
//    be1.setBlockLogTriggerCapsule(mock(BlockLogTriggerCapsule.class));
//
//    BlockingQueue<TriggerCapsule> queue = new BlockingArrayQueue<>();
//    Manager manager = mock(Manager.class);
//    Mockito.when(manager.getTriggerCapsuleQueue()).thenReturn(queue);
//
//    SolidEventService solidEventService = new SolidEventService();
//    ReflectUtils.setFieldValue(solidEventService, "manager", manager);
//    ReflectUtils.setFieldValue(solidEventService, "blockLogTriggerEnable", true);
//    ReflectUtils.setFieldValue(solidEventService, "blockLogTriggerSolidified", true);
//    ReflectUtils.setFieldValue(historyEventService, "solidEventService", solidEventService);
//    ReflectUtils.setFieldValue(historyEventService,
//      "realtimeEventService", mock(RealtimeEventService.class));
//
//    BlockEventGet blockEventGet = mock(BlockEventGet.class);
//    Mockito.when(blockEventGet.getBlockEvent(1)).thenReturn(be1);
//    ReflectUtils.setFieldValue(historyEventService, "blockEventGet", blockEventGet);
//
//    ReflectUtils.setFieldValue(historyEventService, "startSyncBlockNum", 1);
//    ReflectUtils.setFieldValue(historyEventService, "isRunning", true);
//
//    Method method1 = historyEventService.getClass().getDeclaredMethod("syncEvent");
//    method1.setAccessible(true);
//    method1.invoke(historyEventService);
//
//    Assert.assertEquals(1, queue.size());
//  }
}
