package org.tron.program.datastat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.ContractCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.protos.Protocol;
import org.tron.protos.contract.SmartContractOuterClass;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "Data")
public class SunStat2 {
  public static void main(String[] args) throws Exception {
    init();
  }

  public static void init() throws Exception {

    List<TrxDetail> details = new ArrayList<>();

    Set<String> set = new HashSet<>();
    String path = "/data/test/java-tron/f";
//    String path = "/Users/adiswu/git/develop-1/java-tron/f";
//
    String ssss = "e3fb78d36509e9395704237bac7583cc880b6007f06a1421ebf754d97bab241d";

    logger.info("path {}", path);

    byte[] bytes = Files.readAllBytes(Paths.get(path));

    String content = new String(bytes, StandardCharsets.UTF_8);

    String[] sz = content.split("\n");

    for(int i = 0; i < sz.length; i++) {
      if(ssss.equals(sz[i].trim())) {
        logger.info("oooooooooooo");
        break;
      }
      set.add(sz[i].trim());
    }

    logger.info("set size {}, total {}", set.size(), sz.length);

    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("127.0.0.1:50051")
      .usePlaintext().build();
//    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("52.196.244.176:50051")
//      .usePlaintext().build();

    WalletGrpc.WalletBlockingStub blockingStub = WalletGrpc.newBlockingStub(channelFull2);

    long startNum = 65626710;
    long endNum = 65647996;
//    long endNum = 65626810;

    for(long i = startNum; i < endNum; i++) {
      try {
        GrpcAPI.NumberMessage msg = GrpcAPI.NumberMessage.newBuilder().setNum(i).build();
        Protocol.Block block = blockingStub.getBlockByNum(msg);
        int index = 0;
        for(Protocol.Transaction transaction: block.getTransactionsList()) {
          index++;
          TransactionCapsule capsule = new TransactionCapsule(transaction);
          int type = transaction.getRawData().getContract(0).getType().getNumber();
          if (type != Protocol.Transaction.Contract.ContractType.TriggerSmartContract_VALUE) {
            continue;
          }

          SmartContractOuterClass.TriggerSmartContract triggerSmartContract = ContractCapsule.getTriggerContractFromTransaction(transaction);
          if (triggerSmartContract == null) {
            continue;
          }

          byte[] contractAddress = triggerSmartContract.getContractAddress().toByteArray();
          if (!Hex.toHexString(contractAddress).toUpperCase().equals("41FF7155B5DF8008FBF3834922B2D52430B27874F5")) {
            continue;
          }

          if(set.contains(capsule.getTransactionId().toString())){
            continue;
          }

          Param param = Decode.decode(transaction);
          TrxDetail detail = new TrxDetail();
          detail.setTxId(capsule.getTransactionId().toString());
          detail.setBlockNum(get(block.getBlockHeader().getRawData().getNumber()));
          detail.setBlockTime(block.getBlockHeader().getRawData().getTimestamp());
          detail.setWitness(StringUtil.encode58Check(block.getBlockHeader().getRawData().getWitnessAddress().toByteArray()));
          detail.setIndex(index);
          detail.setRet(transaction.getRet(0).toString());
          if(param != null) {
            detail.setAmount(param.getAmount());
            detail.setCallValue(param.getCallValue());
            if (param.getPath().get(1).toLowerCase().equals("41891cdb91d149f23b1a45d9c5ca78a88d0cb44c18")) {
              detail.setToken(StringUtil.encode58Check(Hex.decode(param.getPath().get(0))));
              detail.setBuy(false);
            } else {
              detail.setToken(StringUtil.encode58Check(Hex.decode(param.getPath().get(1))));
              detail.setBuy(true);
            }
          }
          details.add(detail);
        }
      }catch (Exception e) {
        System.out.println(e);
        e.printStackTrace();
      }
    }

    details.forEach(d -> {
      logger.info("{}", JsonUtil.obj2Json(d));
    });

    details.forEach(d -> {
      logger.info("{}", d);
    });
  }

  public static String get(long timestamp) {
    Instant instant = Instant.ofEpochMilli(timestamp);

    LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toString();

  }
}
