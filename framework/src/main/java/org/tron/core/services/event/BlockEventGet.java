package org.tron.core.services.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI;
import org.tron.common.logsfilter.EventPluginLoader;
import org.tron.common.logsfilter.capsule.BlockLogTriggerCapsule;
import org.tron.common.logsfilter.capsule.TransactionLogTriggerCapsule;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.capsule.TransactionRetCapsule;
import org.tron.core.db.Manager;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j(topic = "event")
@Component
public class BlockEventGet {

  @Autowired
  private Manager manager;

  public BlockEvent getBlockEvent(long blockNum) throws Exception {
    BlockCapsule block = manager.getChainBaseManager().getBlockByNum(blockNum);

    long solidNum = manager.getDynamicPropertiesStore().getLatestSolidifiedBlockNum();

    BlockEvent blockEvent = new BlockEvent();

    blockEvent.setBlockId(block.getBlockId());
    blockEvent.setParentId(block.getParentBlockId());
    blockEvent.setBlockTime(block.getTimeStamp());
    blockEvent.setSolidNum(solidNum);
    blockEvent.setEnergyFee(0);

    if (EventPluginLoader.getInstance().isBlockLogTriggerEnable()) {
      blockEvent.setBlockLogTriggerCapsule(getBlockLogTrigger(block, solidNum));
    }

    if(EventPluginLoader.getInstance().isTransactionLogTriggerEnable()) {
      blockEvent.setTransactionLogTriggerCapsules(getTransactionLogTrigger(block, solidNum));
    }

    if (EventPluginLoader.getInstance().isContractEventTriggerEnable() ||
      EventPluginLoader.getInstance().isContractLogTriggerEnable() ||
      EventPluginLoader.getInstance().isSolidityEventTriggerEnable() ||
      EventPluginLoader.getInstance().isSolidityLogTriggerEnable()) {
      blockEvent.setSmartContractTrigger(getContractTrigger(block, solidNum));
    }

    return blockEvent;
  }

  public SmartContractTrigger getContractTrigger(BlockCapsule block, long solidNum) {
    //todo
    return null;
  }

  public BlockLogTriggerCapsule getBlockLogTrigger(BlockCapsule block, long solidNum) {
    BlockLogTriggerCapsule blockLogTriggerCapsule = new BlockLogTriggerCapsule(block);
    blockLogTriggerCapsule.setLatestSolidifiedBlockNumber(solidNum);
    return blockLogTriggerCapsule;
  }


  public List<TransactionLogTriggerCapsule> getTransactionLogTrigger(BlockCapsule block, long solidNum) {
    //todo get energyUnitPrice
    long energyUnitPrice = 0;

    List<TransactionLogTriggerCapsule> transactionLogTriggerCapsules = new ArrayList<>();
    if (!EventPluginLoader.getInstance().isTransactionLogTriggerEthCompatible()) {
      for (TransactionCapsule t : block.getTransactions()) {
        TransactionLogTriggerCapsule trx = new TransactionLogTriggerCapsule(t, block);
        trx.setLatestSolidifiedBlockNumber(solidNum);
        transactionLogTriggerCapsules.add(trx);
      }
      return transactionLogTriggerCapsules;
    }

    List<TransactionCapsule> transactionCapsuleList = block.getTransactions();
    GrpcAPI.TransactionInfoList transactionInfoList = GrpcAPI.TransactionInfoList.newBuilder().build();
    GrpcAPI.TransactionInfoList.Builder transactionInfoListBuilder = GrpcAPI.TransactionInfoList.newBuilder();

    try {
      TransactionRetCapsule result = manager.getChainBaseManager().getTransactionRetStore()
        .getTransactionInfoByBlockNum(ByteArray.fromLong(block.getNum()));
      if (!Objects.isNull(result) && !Objects.isNull(result.getInstance())) {
        result.getInstance().getTransactioninfoList().forEach(
          transactionInfoListBuilder::addTransactionInfo
        );
        transactionInfoList = transactionInfoListBuilder.build();
      }
    } catch (BadItemException e) {
      logger.error("Get TransactionInfo failed, blockNum {}, {}.", block.getNum(), e.getMessage());
    }

    if (transactionCapsuleList.size() != transactionInfoList.getTransactionInfoCount()) {
      logger.error("Get TransactionInfo size not eq, blockNum {}, {}, {}",
        block.getNum(), transactionCapsuleList.size(), transactionInfoList.getTransactionInfoCount());
      for (TransactionCapsule t : block.getTransactions()) {
        TransactionLogTriggerCapsule trx = new TransactionLogTriggerCapsule(t, block);
        trx.setLatestSolidifiedBlockNumber(solidNum);
        transactionLogTriggerCapsules.add(trx);
      }
      return transactionLogTriggerCapsules;
    }

    long cumulativeEnergyUsed = 0;
    long cumulativeLogCount = 0;
    for (int i = 0; i < transactionCapsuleList.size(); i++) {
      Protocol.TransactionInfo transactionInfo = transactionInfoList.getTransactionInfo(i);
      TransactionCapsule transactionCapsule = transactionCapsuleList.get(i);
      transactionCapsule.setBlockNum(block.getNum());
      TransactionLogTriggerCapsule trx = new TransactionLogTriggerCapsule(transactionCapsule, block,
        i, cumulativeEnergyUsed, cumulativeLogCount, transactionInfo, energyUnitPrice);
      trx.setLatestSolidifiedBlockNumber(solidNum);
      cumulativeLogCount += trx.getTransactionLogTrigger().getEnergyUsageTotal();
      transactionLogTriggerCapsules.add(trx);
    }
    return transactionLogTriggerCapsules;
  }
}
