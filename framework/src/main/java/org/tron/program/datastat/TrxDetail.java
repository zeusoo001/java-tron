package org.tron.program.datastat;

import lombok.Data;

import java.math.BigInteger;

@Data
public class TrxDetail {
  private String txId;
  private long blockNum;
  private long blockTime;
  private BigInteger amount;
  private long callValue;
  private String token;
  private int index;
  private boolean isBuy;
  private String witness;
  private String ret;

  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(blockNum).append(",").append(blockTime).append(",")
      .append(amount).append(",").append(callValue).append(",")
      .append(token).append(",").append(index).append(",").append(isBuy).append(",").append(witness).append(",").append(ret);

    return b.toString();
  }
}
