package org.tron.core.net.service.sync;

import lombok.Data;
import org.tron.core.capsule.BlockCapsule;

@Data
public class BlockTmp {
  private BlockCapsule.BlockId blockId;
  private byte[] data;
}
