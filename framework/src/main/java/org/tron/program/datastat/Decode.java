package org.tron.program.datastat;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.BigIntegers;
import org.tron.common.utils.ByteArray;
import org.tron.core.capsule.ContractCapsule;
import org.tron.protos.Protocol;
import org.tron.protos.contract.SmartContractOuterClass;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.tron.program.datastat.Param.SunType.swapExactETHForTokens;
import static org.tron.program.datastat.Param.SunType.swapExactTokensForETH;
import static org.tron.program.datastat.Param.SunType.other;

@Slf4j(topic = "sun")
public class Decode {

  public static Param decode(Protocol.Transaction transaction) {

    int type = transaction.getRawData().getContract(0).getType().getNumber();
    if (type != Protocol.Transaction.Contract.ContractType.TriggerSmartContract_VALUE) {
      return null;
    }

    SmartContractOuterClass.TriggerSmartContract contract = ContractCapsule.getTriggerContractFromTransaction(transaction);
    if (contract == null) {
      return null;
    }

    SmartContractOuterClass.TriggerSmartContract triggerSmartContract = ContractCapsule.getTriggerContractFromTransaction(transaction);
    if (triggerSmartContract == null) {
      return null;
    }

    byte[] contractAddress = triggerSmartContract.getContractAddress().toByteArray();
    if (!Hex.encodeHexString(contractAddress).toUpperCase().equals("41FF7155B5DF8008FBF3834922B2D52430B27874F5")) {
      return null;
    }

    byte[] source = contract.getData().toByteArray();

    String s = Hex.encodeHexString(source);
//    System.out.println(s);

    if (s.startsWith("18cbafe5")) {
      return swapExactTokensForETH(source);
    }

    if (s.startsWith("7ff36ab5")) {
      return swapExactETHForTokens(source, contract.getCallValue());
    }

    if (s.startsWith("fb3bdb41")) {
      return swapETHForExactTokens(source, contract.getCallValue());
    }

    return swapOther(source);
  }

  //swapExactTokensForETH(uint256 amountIn, uint256 amountOutMin, address[] path, address to, uint256 deadline)
  public static Param swapExactTokensForETH(byte[] source) {
    List<byte[]> list = new ArrayList<>();
    for(int i = 7; i >= 0; i--) {
      list.add(ByteArray.subArray(source, source.length - (i + 1) * 32, source.length - i * 32));
    }

    BigInteger amount = BigIntegers.fromUnsignedByteArray(list.get(0));
    BigInteger amountT = BigIntegers.fromUnsignedByteArray(list.get(1));
    String to = "41" + Hex.encodeHexString(ByteArray.subArray(list.get(3), 12, 32));
    long deadline = BigIntegers.fromUnsignedByteArray(list.get(4)).longValue();
    List<String> path = new ArrayList<>();
    path.add("41" + Hex.encodeHexString(ByteArray.subArray(list.get(6), 12, 32)));
    path.add("41" + Hex.encodeHexString(ByteArray.subArray(list.get(7), 12, 32)));

    Param param = new Param(swapExactTokensForETH, amount, amountT, path, to, deadline);
    return param;
  }

  //swapExactETHForTokens(uint256 amountOutMin, address[] path, address to, uint256 deadline)
  //deb212e7d81add1faadd483126f46eb0a44db0c93937f4b99556a6f45b3d8b43
  //online
  public static Param swapExactETHForTokens(byte[] source, long callValue) {
    List<byte[]> list = new ArrayList<>();
    for(int i = 6; i >= 0; i--) {
      list.add(ByteArray.subArray(source, source.length - (i + 1) * 32, source.length - i * 32));
    }
    BigInteger amount = BigIntegers.fromUnsignedByteArray(list.get(0));
    String to = "41" + Hex.encodeHexString(ByteArray.subArray(list.get(2), 12, 32));
    long deadline = BigIntegers.fromUnsignedByteArray(list.get(3)).longValue();
    List<String> path = new ArrayList<>();
    path.add("41" + Hex.encodeHexString(ByteArray.subArray(list.get(5), 12, 32)));
    path.add("41" + Hex.encodeHexString(ByteArray.subArray(list.get(6), 12, 32)));

    Param param = new Param(swapExactETHForTokens, amount, amount, path, to, deadline, callValue);
    return param;
  }

  public static Param swapOther(byte[] source) {
    Param param = null;
    if (source.length > 8 * 32) {
      param = swapExactTokensForETH(source);
      logger.info("@@@@@ {}, {}", param.getPath().get(0), param.getPath().get(1));
    } else if (source.length > 7 * 32) {
      param = swapExactETHForTokens(source, 0l);
    }
    if (param != null) {
      param.setType(other);
    }
    return param;
  }

  //swapETHForExactTokens(uint256 amountOut, address[] path, address to, uint256 deadline)
  //eb86b1ea5e2df76f613b108a1bd78f91a41a4ca29b1caaa5aee66e8b14f02a6d
  public static Param swapETHForExactTokens(byte[] source, long callValue) {
    Param param = swapExactETHForTokens(source, callValue);
    param.setType(other);
    return param;
  }

}

