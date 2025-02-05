package org.tron.core.services.event;

import lombok.Data;
import org.tron.core.capsule.BlockCapsule;

@Data
public class Event {
  private BlockCapsule.BlockId blockId;
  private boolean isRemove;

  public Event (BlockCapsule.BlockId blockId, boolean isRemove) {
    this.blockId = blockId;
    this.isRemove = isRemove;
  }
}
