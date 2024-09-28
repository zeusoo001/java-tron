package org.tron.program.datastat;

import lombok.Data;
import org.tron.core.capsule.TransactionCapsule;

import java.math.BigInteger;
import java.util.List;

@Data
public class Param {

  SunType type;
  BigInteger amount;
  BigInteger amountT;
  List<String> path;
  String to;
  long deadLine;
  long callValue;
  TransactionCapsule capsule;

  public Param(SunType type, BigInteger amount, BigInteger amountT, List<String> path, String to, long deadLine) {
    this.type = type;
    this.amount = amount;
    this.amountT = amountT;
    this.path = path;
    this.to = to;
    this.deadLine = deadLine;
  }

  public Param(SunType type, BigInteger amount, BigInteger amountT, List<String> path, String to, long deadLine, long callValue) {
    this.type = type;
    this.amount = amount;
    this.amountT = amountT;
    this.path = path;
    this.to = to;
    this.deadLine = deadLine;
    this.callValue = callValue;
  }

  public String toString() {
    return  "type : " + type
      + ", amount : " + amount
      + ", amountT : " + amountT
      + ", path : " + path
      + ", to : " + to
      + ", deadLine : " + deadLine
      + ", callValue : " + callValue;
  }

  public enum SunType {
    swapETHForExactTokens,
    swapExactETHForTokens,
    swapExactTokensForETH,
    other;
  }
}

