package org.tron.core.services.event;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tron.common.es.ExecutorServiceManager;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.capsule.SolidityTriggerCapsule;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j(topic = "event")
public class BlockEventLoad {
  private boolean blockLogTriggerEnable = EventPluginLoader.getInstance().isBlockLogTriggerEnable();
  private boolean transactionLogTriggerEnable = EventPluginLoader.getInstance().isTransactionLogTriggerEnable();
  private boolean contractLogTriggerEnable = EventPluginLoader.getInstance().isContractLogTriggerEnable();
  private boolean contractEventTriggerEnable = EventPluginLoader.getInstance().isContractEventTriggerEnable();
  private boolean solidityLogTriggerEnable = EventPluginLoader.getInstance().isSolidityLogTriggerEnable();
  private boolean solidityEventTriggerEnable = EventPluginLoader.getInstance().isSolidityEventTriggerEnable();
  private boolean solidityTriggerEnable = EventPluginLoader.getInstance().isSolidityTriggerEnable();

  @Autowired
  private Manager manager;

  @Autowired
  private RealtimeEventService realtimeEventService;

  @Autowired
  private BlockEventGet blockEventGet;

  private final ScheduledExecutorService executor = ExecutorServiceManager
    .newSingleThreadScheduledExecutor("event-load");

  public void init () {
    executor.scheduleWithFixedDelay(() -> {
      try {
        load();
      } catch (Exception exception) {
        close();
        logger.error("Spread thread error", exception);
      }
    }, 100, 100, TimeUnit.MILLISECONDS);
    logger.info("Event load service start.");
  }

  public void close() {
    executor.shutdown();
    logger.info("Event load service close.");
  }

  public void load() throws Exception {
    long cacheHeadNum = BlockEventCache.getHead().getBlockId().getNum();
    long tmpNum =  manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();
    if (cacheHeadNum >= tmpNum) {
      return;
    }
    synchronized (manager) {
      tmpNum =  manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber();
      if (cacheHeadNum >= tmpNum) {
        return;
      }
      List<BlockEvent> l1 = new ArrayList<>();
      List<BlockEvent> l2 = new ArrayList<>();
      BlockEvent tmp = BlockEventCache.getHead();
      while (true) {
        BlockEvent blockEvent = getBlockEvent(tmpNum);
        l1.add(blockEvent);
        if (blockEvent.getParentId().equals(tmp.getBlockId())) {
          break;
        }

        tmpNum--;
        if (tmpNum == tmp.getBlockId().getNum()) {
          l2.add(tmp);
          tmp = BlockEventCache.getBlockEvent(tmp.getParentId());
        }
      }

      l2.forEach(e -> realtimeEventService.add(new Event(e.getBlockId(), true)));

      List<BlockEvent> l = Lists.reverse(l1);
      for (BlockEvent e: l) {
        BlockEventCache.add(e);
        realtimeEventService.add(new Event(e.getBlockId(), false));
      }
    }
  }

  public BlockEvent getBlockEvent(long blockNum) throws Exception {
    BlockCapsule blockCapsule = manager.getChainBaseManager().getBlockByNum(blockNum);
    long solidNum = manager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();
    BlockEvent blockEvent = new BlockEvent();
    if (blockLogTriggerEnable) {
      blockEvent.setBlockLogTriggerCapsule(blockEventGet.getBlockLogTrigger(blockCapsule, solidNum));
    }

    if (transactionLogTriggerEnable) {
      //todo energyUnitPrice
      blockEvent.setTransactionLogTriggerCapsules(blockEventGet.getTransactionLogTrigger(blockCapsule, solidNum));
    }

    if (contractEventTriggerEnable || contractLogTriggerEnable || solidityEventTriggerEnable || solidityLogTriggerEnable) {
      blockEvent.setSmartContractTrigger(blockEventGet.getContractTrigger(blockCapsule, solidNum));
    }

    if (solidityTriggerEnable) {
      SolidityTriggerCapsule solidityTriggerCapsule
        = new SolidityTriggerCapsule(blockCapsule.getNum());
      solidityTriggerCapsule.setTimeStamp(blockCapsule.getTimeStamp());
      blockEvent.setSolidityTriggerCapsule(solidityTriggerCapsule);
    }

    return blockEvent;
  }
}
