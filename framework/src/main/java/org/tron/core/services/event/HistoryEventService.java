package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;

@Slf4j(topic = "event")
@Component
public class HistoryEventService {

  private long startSyncBlockNum = EventPluginLoader.getInstance().getStartSyncBlockNum();

  @Autowired
  private BlockEventGet blockEventGet;

  @Autowired
  private SolidEventService solidEventService;

  @Autowired
  private RealtimeEventService realtimeEventService;

  @Autowired
  private BlockEventLoad blockEventLoad;

  private Manager manager;

  private volatile boolean isRunning;

  public void init(Manager manager) {
    this.manager = manager;
    BlockCapsule.BlockId blockId = manager.getChainBaseManager().getHeadBlockId();
    if (startSyncBlockNum <= 0) {
      initEventService(blockId);
      return;
    }

    isRunning = true;

    new Thread(() -> syncEvent()).start();

    logger.info("History event service start.");
  }

  public void close() {
    isRunning = false;
  }

  private void syncEvent() {
    try {
      long tmp = startSyncBlockNum;
      long endNum = manager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
      while (tmp <= endNum && isRunning) {
        BlockEvent blockEvent = blockEventGet.getBlockEvent(tmp);
        realtimeEventService.flush(blockEvent, false);
        solidEventService.flush(blockEvent);
        tmp++;
        endNum = manager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
        Thread.sleep(30);
      }
      initEventService(manager.getChainBaseManager().getBlockIdByNum(endNum));
    } catch (Exception e) {
      logger.error("Sync event failed. {}", e);
    }
  }

  private void initEventService(BlockCapsule.BlockId blockId) {
    logger.info("Init event service, {}", blockId.getString());
    BlockEventCache.init(blockId);
    realtimeEventService.init();
    blockEventLoad.init();
    solidEventService.init();
  }
}
