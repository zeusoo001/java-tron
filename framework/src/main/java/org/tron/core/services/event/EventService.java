package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.core.db.Manager;

@Slf4j(topic = "event")
@Component
public class EventService {
  @Autowired
  private RealtimeEventService realtimeEventService;

  @Autowired
  private BlockEventLoad blockEventLoad;

  @Autowired
  private BlockEventGet blockEventGet;

  @Autowired
  private HistoryEventService historyEventService;

  @Autowired
  private Manager manager;

  public void init()  {
    logger.error("Start to load eventPlugin. {}, {}, {} " +
        "block: {}, {} trx: {}, {} event: {}, {} log: {}, {}, {}, {}, {} solid: {}",
      manager.isEventPluginLoaded(),

      EventPluginLoader.getInstance().getVersion(),
      EventPluginLoader.getInstance().getStartSyncBlockNum(),

      EventPluginLoader.getInstance().isBlockLogTriggerEnable(),
      EventPluginLoader.getInstance().isBlockLogTriggerSolidified(),

      EventPluginLoader.getInstance().isTransactionLogTriggerEnable(),
      EventPluginLoader.getInstance().isTransactionLogTriggerSolidified(),

      EventPluginLoader.getInstance().isContractEventTriggerEnable(),
      EventPluginLoader.getInstance().isSolidityEventTriggerEnable(),

      EventPluginLoader.getInstance().isContractLogTriggerEnable(),
      EventPluginLoader.getInstance().isSolidityLogTriggerEnable(),
      EventPluginLoader.getInstance().isContractLogTriggerRedundancy(),
      EventPluginLoader.getInstance().isSolidityLogTriggerRedundancy(),
      EventPluginLoader.getInstance().isTransactionLogTriggerEthCompatible(),

      EventPluginLoader.getInstance().isSolidityTriggerEnable());

    if (!manager.isEventPluginLoaded() || EventPluginLoader.getInstance().getVersion() != 1) {
      return;
    }

    historyEventService.init(manager);
  }

  public void close() {
    realtimeEventService.close();
    blockEventLoad.close();
    historyEventService.close();
  }
}
