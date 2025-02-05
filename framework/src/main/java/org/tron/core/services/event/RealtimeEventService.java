package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.es.ExecutorServiceManager;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.trigger.Trigger;
import org.tron.core.db.Manager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "event")
@Component
public class RealtimeEventService {
  private boolean blockLogTriggerEnable = EventPluginLoader.getInstance().isBlockLogTriggerEnable();
  private boolean blockLogTriggerSolidified = EventPluginLoader.getInstance().isBlockLogTriggerSolidified();
  private boolean transactionLogTriggerEnable = EventPluginLoader.getInstance().isTransactionLogTriggerEnable();
  private boolean transactionLogTriggerSolidified = EventPluginLoader.getInstance().isTransactionLogTriggerSolidified();
  private boolean contractLogTriggerEnable = EventPluginLoader.getInstance().isContractLogTriggerEnable();
  private boolean contractEventTriggerEnable = EventPluginLoader.getInstance().isContractEventTriggerEnable();
  private boolean contractLogTriggerRedundancy = EventPluginLoader.getInstance().isContractLogTriggerRedundancy();

  @Autowired
  private Manager manager;

  @Autowired
  private SolidEventService solidEventService;

  private static BlockingQueue<Event> queue = new LinkedBlockingQueue<>();

  private int maxEventSize = 10000;

  private final ScheduledExecutorService executor = ExecutorServiceManager
    .newSingleThreadScheduledExecutor("realtime-event");

  public void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        work();
      } catch (Exception e) {
        logger.info("Real-time event service fail. {}", e);
      }
    }, 1, 1, TimeUnit.SECONDS);
    logger.info("Realtime event service start.");
  }

  public void close() {
    executor.shutdown();
    logger.info("Realtime event service close.");
  }

  public void add(Event event) {
    if (queue.size() >= maxEventSize) {
      logger.warn("Add event failed, blockId {}.", event.getBlockId());
      return;
    }
    queue.offer(event);
  }

  public void work() {
    while (queue.size() > 0) {
      Event event = queue.poll();
      BlockEvent blockEvent = BlockEventCache.getBlockEvent(event.getBlockId());
      flush(blockEvent, event.isRemove());
      if (!event.isRemove()) {
        solidEventService.work();
      }
    }
  }

  public void flush(BlockEvent blockEvent, boolean isRemove) {
    logger.info("Flush realtime event {}", blockEvent.getBlockId().getString());

    if (blockLogTriggerEnable && !blockLogTriggerSolidified) {
      if (blockEvent.getBlockLogTriggerCapsule() == null) {
        logger.warn("BlockLogTriggerCapsule is null. {}", blockEvent.getBlockId().getString());
      } else {
        manager.getTriggerCapsuleQueue().offer(blockEvent.getBlockLogTriggerCapsule());
      }
    }

    if (transactionLogTriggerEnable && !transactionLogTriggerSolidified) {
      if (blockEvent.getTransactionLogTriggerCapsules() == null) {
        logger.info("TransactionLogTriggerCapsules is null. {}", blockEvent.getBlockId().getString());
      } else {
        blockEvent.getTransactionLogTriggerCapsules().forEach(v ->
          manager.getTriggerCapsuleQueue().offer(v));
      }
    }

    if (contractLogTriggerEnable) {
      if (blockEvent.getSmartContractTrigger() == null) {
        logger.info("SmartContractTrigger is null. {}", blockEvent.getBlockId().getString());
      } else {
        blockEvent.getSmartContractTrigger().getContractLogTriggers().forEach(v -> {
          v.setTriggerName(Trigger.CONTRACTLOG_TRIGGER_NAME);
          v.setRemoved(isRemove);
          EventPluginLoader.getInstance().postSolidityLogTrigger(v);
        });
      }
    }

    if (contractEventTriggerEnable) {
      if (blockEvent.getSmartContractTrigger() == null) {
        logger.info("SmartContractTrigger is null. {}", blockEvent.getBlockId().getString());
      } else {
        blockEvent.getSmartContractTrigger().getContractEventTriggers().forEach(v -> {
          v.setTriggerName(Trigger.CONTRACTEVENT_TRIGGER_NAME);
          v.setRemoved(isRemove);
          EventPluginLoader.getInstance().postSolidityEventTrigger(v);
        });
      }
    }
  }

}
