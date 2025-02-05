package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.core.db.Manager;

@Slf4j(topic = "event")
@Component
public class EventService {
  @Autowired
  private RealtimeEventService realtimeEventService;

  @Autowired
  private BlockEventLoad blockEventLoad;

  @Autowired
  private HistoryEventService historyEventService;

  public void init(Manager manager) throws Exception {
    BlockEventCache.init(manager.getChainBaseManager().getHeadBlockId());
    realtimeEventService.init();
    blockEventLoad.init();
    historyEventService.init(manager.getChainBaseManager().getHeadBlockId().getNum());
  }

  public void close() {
    realtimeEventService.close();
    blockEventLoad.close();
    historyEventService.close();
  }
}
