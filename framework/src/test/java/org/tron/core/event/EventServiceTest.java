package org.tron.core.event;

import org.junit.Test;
import org.mockito.Mockito;
import org.tron.common.logsfilter.EventLoaderTest;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.ChainBaseManager;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;
import org.tron.core.services.event.*;

import static org.mockito.Mockito.mock;

public class EventServiceTest {

  @Test
  public void test() throws Exception {
    EventService eventService = new EventService();
    HistoryEventService historyEventService = new HistoryEventService();
    RealtimeEventService realtimeEventService = new RealtimeEventService();
    BlockEventLoad blockEventLoad = new BlockEventLoad();

    ReflectUtils.setFieldValue(eventService, "historyEventService", historyEventService);
    ReflectUtils.setFieldValue(eventService, "realtimeEventService", realtimeEventService);
    ReflectUtils.setFieldValue(eventService, "blockEventLoad", blockEventLoad);

    ChainBaseManager chainBaseManager = mock(ChainBaseManager.class);

    Manager manager = mock(Manager.class);
    Mockito.when(manager.getChainBaseManager()).thenReturn(chainBaseManager);
    Mockito.when(chainBaseManager.getHeadBlockId()).thenReturn(mock(BlockCapsule.BlockId.class));

    eventService.init(manager);
    eventService.close();
  }
}
