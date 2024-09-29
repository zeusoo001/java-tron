package org.tron.program.datastat;

import lombok.Data;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.protos.Protocol;

import java.util.ArrayList;
import java.util.List;

@Data
public class Result2 {
  private String leftTx;
  private List<String> txs = new ArrayList<>();
  private String rightTx;
  private long blockNum;
  private long blockTime;
  private String witness;

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(blockNum).append(",").append(blockTime).append(",");
    if(leftTx != null) {
      builder.append(leftTx).append(",");
    }
    if(txs.size() > 0) {
      txs.forEach(t -> builder.append(t).append(","));
    }
    if(rightTx != null) {
      builder.append(rightTx).append(",");
    }
    builder.append(witness);

    return builder.toString();

  }

}
