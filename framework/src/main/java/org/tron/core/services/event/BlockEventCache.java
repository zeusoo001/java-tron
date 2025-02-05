package org.tron.core.services.event;

import lombok.Getter;
import org.tron.core.capsule.BlockCapsule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockEventCache {
  @Getter
  private static volatile long solidNum;

  @Getter
  private static volatile BlockEvent head;

  private static Map<BlockCapsule.BlockId, BlockEvent> blockEventMap = new ConcurrentHashMap<>();

  private static Map<Long, List<BlockEvent>> numMap = new ConcurrentHashMap<>();

  public static BlockEvent getBlockEvent(BlockCapsule.BlockId blockId) {
    return blockEventMap.get(blockId);
  }

  public static void init(BlockCapsule.BlockId blockId) {
    blockEventMap.clear();
    numMap.clear();
    solidNum = blockId.getNum();
    head = new BlockEvent(blockId);
    List<BlockEvent> list = new ArrayList<>();
    list.add(head);
    numMap.put(blockId.getNum(), list);
    blockEventMap.put(blockId, head);
  }

  public static void add(BlockEvent blockEvent) throws EventException {
    if (blockEventMap.get(blockEvent.getParentId()) == null) {
      throw new EventException("unlink BlockEvent, "
        + blockEvent.getBlockId().getString() + ", "
        + blockEvent.getParentId().getString());
    }

    long num = blockEvent.getBlockId().getNum();
    List<BlockEvent> list = numMap.get(num);
    if (list == null) {
      list = new ArrayList<>();
      numMap.put(num, list);
    }
    list.add(blockEvent);

    blockEventMap.put(blockEvent.getBlockId(), blockEvent);

    if (num > head.getBlockId().getNum()) {
      head = blockEvent;
    }
  }

  public static void remove(BlockCapsule.BlockId solidId) {
    numMap.forEach((k, v) -> {
      if (k <= solidId.getNum()) {
        v.forEach(value -> blockEventMap.remove(value.getBlockId()));
        numMap.remove(k);
      }
    });
    solidNum = solidId.getNum();
  }

  public static List<BlockEvent> getSolidBlockEvents(BlockCapsule.BlockId solidId) {
    List<BlockEvent> blockEvents = new ArrayList<>();
    BlockCapsule.BlockId tmp = solidId;
    while (tmp.getNum() > solidNum) {
      BlockEvent blockEvent = blockEventMap.get(tmp);
      blockEvents.add(blockEvent);
      tmp = blockEvent.getParentId();
    }
    return blockEvents;
  }
}
