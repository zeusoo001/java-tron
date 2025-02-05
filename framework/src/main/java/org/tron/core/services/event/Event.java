package org.tron.core.services.event;

import lombok.Data;
import org.tron.core.capsule.BlockCapsule;

@Data
public class Event {
  private boolean isRemove;
  private BlockEvent blockEvent;

  public Event (BlockEvent blockEvent, boolean isRemove) {
    this.blockEvent = blockEvent;
    this.isRemove = isRemove;
  }
}
