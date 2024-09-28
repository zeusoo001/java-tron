package org.tron.program.datastat;

import lombok.Data;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.protos.Protocol;

import java.util.ArrayList;
import java.util.List;

@Data
public class Result {
  private Protocol.Transaction leftTx;
  private List<Protocol.Transaction> txs = new ArrayList<>();
  private Protocol.Transaction rightTx;
  private long blockNum;
  private long blockTime;
  private String witness;

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(blockNum).append(",").append(blockTime).append(",");
    if(leftTx != null) {
      builder.append(new TransactionCapsule(leftTx).getTransactionId()).append(",");
    }
    if(txs.size() > 0) {
      txs.forEach(t -> builder.append(new TransactionCapsule(t).getTransactionId()).append(","));
    }
    if(rightTx != null) {
      builder.append(new TransactionCapsule(rightTx).getTransactionId()).append(",");
    }
    builder.append(witness);

    return builder.toString();

  }

}
