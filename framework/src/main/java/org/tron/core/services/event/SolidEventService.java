package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.trigger.Trigger;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.db.Manager;

import java.util.List;

@Slf4j(topic = "event")
@Component
public class SolidEventService {
  private boolean blockLogTriggerEnable = EventPluginLoader.getInstance().isBlockLogTriggerEnable();
  private boolean blockLogTriggerSolidified = EventPluginLoader.getInstance().isBlockLogTriggerSolidified();
  private boolean transactionLogTriggerEnable = EventPluginLoader.getInstance().isTransactionLogTriggerEnable();
  private boolean transactionLogTriggerSolidified = EventPluginLoader.getInstance().isTransactionLogTriggerSolidified();
  private boolean solidityLogTriggerEnable = EventPluginLoader.getInstance().isSolidityLogTriggerEnable();
  private boolean solidityEventTriggerEnable = EventPluginLoader.getInstance().isSolidityEventTriggerEnable();
  private boolean solidityTriggerEnable = EventPluginLoader.getInstance().isSolidityTriggerEnable();

  @Autowired
  private Manager manager;

//  private final ScheduledExecutorService executor = ExecutorServiceManager
//    .newSingleThreadScheduledExecutor("solid-event");
//
//  public void init() {
//    executor.scheduleWithFixedDelay(() -> {
//      try {
//        work();
//      } catch (Exception exception) {
//        logger.error("Spread thread error", exception);
//      }
//    }, 1, 1, TimeUnit.SECONDS);
//    logger.info("Solid event service start.");
//  }
//
//  public void close() {
//    executor.shutdown();
//    logger.info("Solid event service close.");
//  }

  public void work() {
    BlockCapsule.BlockId solidId = manager.getChainBaseManager().getSolidBlockId();
    if (solidId.getNum() <= BlockEventCache.getSolidNum()) {
      return;
    }

    List<BlockEvent> blockEvents = BlockEventCache.getSolidBlockEvents(solidId);

    blockEvents.forEach(v -> flush(v));

    BlockEventCache.remove(solidId);
  }

  public void flush(BlockEvent blockEvent) {
    logger.info("Flush solid event {}", blockEvent.getBlockId().getString());

    if (blockLogTriggerEnable && blockLogTriggerSolidified) {
      if (blockEvent.getBlockLogTriggerCapsule() == null) {
        logger.warn("BlockLogTrigger is null. {}", blockEvent.getBlockId());
      } else {
        manager.getTriggerCapsuleQueue().offer(blockEvent.getBlockLogTriggerCapsule());
      }
    }

    if (transactionLogTriggerEnable && transactionLogTriggerSolidified) {
      if (blockEvent.getTransactionLogTriggerCapsules() == null) {
        logger.info("TransactionLogTrigger is null. {}", blockEvent.getBlockId());
      } else {
        blockEvent.getTransactionLogTriggerCapsules().forEach(v ->
          manager.getTriggerCapsuleQueue().offer(v));
      }
    }

    if (solidityLogTriggerEnable) {
      if (blockEvent.getSmartContractTrigger() == null) {
        logger.info("SmartContractTrigger is null. {}", blockEvent.getBlockId());
      } else {
        blockEvent.getSmartContractTrigger().getContractLogTriggers().forEach(v -> {
          v.setTriggerName(Trigger.SOLIDITYLOG_TRIGGER_NAME);
          EventPluginLoader.getInstance().postSolidityLogTrigger(v);
        });
      }
    }

    if (solidityEventTriggerEnable) {
      if (blockEvent.getSmartContractTrigger() == null) {
        logger.info("SmartContractTrigger is null. {}", blockEvent.getBlockId());
      } else {
        blockEvent.getSmartContractTrigger().getContractEventTriggers().forEach(v -> {
          v.setTriggerName(Trigger.SOLIDITYEVENT_TRIGGER_NAME);
          EventPluginLoader.getInstance().postSolidityEventTrigger(v);
        });
      }
    }

    if (solidityTriggerEnable) {
      if (blockEvent.getSolidityTriggerCapsule() == null) {
        logger.info("SolidityTrigger is null. {}", blockEvent.getBlockId());
      } else {
        manager.getTriggerCapsuleQueue().offer(blockEvent.getSolidityTriggerCapsule());
      }
    }
  }

}
