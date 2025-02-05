package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.logsfilter.EventPluginLoader;

@Slf4j(topic = "event")
@Component
public class HistoryEventService {

  private long startSyncBlockNum = EventPluginLoader.getInstance().getStartSyncBlockNum();

  @Autowired
  private BlockEventLoad blockEventLoad;

  @Autowired
  private SolidEventService solidEventService;

  @Autowired
  private RealtimeEventService realtimeEventService;

  private volatile boolean isRunning;

  public void init(long headBlockNum) {
    if (startSyncBlockNum == 0) {
      return;
    }

    if (startSyncBlockNum > headBlockNum) {
      logger.info("startSyncBlockNum {} gt headBlockNum {}.",
          startSyncBlockNum, headBlockNum);
      return;
    }

    isRunning = true;

    new Thread(() -> syncEvent(headBlockNum)).start();

    logger.info("History event service start.");
  }

  public void close() {
    isRunning = false;
  }

  private void syncEvent(Long headBlockNum) {
    try {
      long tmp = startSyncBlockNum;
      while (tmp <= headBlockNum && isRunning) {
        BlockEvent blockEvent = blockEventLoad.getBlockEvent(tmp);
        realtimeEventService.flush(blockEvent, false);
        solidEventService.flush(blockEvent);
        tmp++;
        Thread.sleep(50);
      }
    } catch (Exception e) {
      logger.error("Sync event failed. {}", e);
    }
  }
}
