package org.tron.program.datastat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "Data")
public class SunStat {

  public static void init() throws Exception {



    Set<String> set = new HashSet<>();
    String path = "/data/test/java-tron/f";
//                String path = "/Users/adiswu/git/develop-1/java-tron/f";
//
    logger.info("path {}", path);

    byte[] bytes = Files.readAllBytes(Paths.get(path));

    String content = new String(bytes, StandardCharsets.UTF_8);

    String[] sz = content.split("\n");

    for(int i = 0; i < sz.length; i++) {
      set.add(sz[i]);
    }

    logger.info("## set size {}", set.size());

    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("127.0.0.1:50051")
      .usePlaintext().build();
//    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("52.196.244.176:50051")
//      .usePlaintext().build();

    WalletGrpc.WalletBlockingStub blockingStub = WalletGrpc.newBlockingStub(channelFull2);

    long startNum = 65380742;
    long endNum = 65582286;

    List<Result> results = new ArrayList<>();

    Result tmp = new Result();
    Param param = null;
    for(long i = startNum; i < endNum; i++) {
      try {
        GrpcAPI.NumberMessage msg = GrpcAPI.NumberMessage.newBuilder().setNum(i).build();
        Protocol.Block block = blockingStub.getBlockByNum(msg);

        tmp.setBlockNum(block.getBlockHeader().getRawData().getNumber());
        tmp.setBlockTime(block.getBlockHeader().getRawData().getTimestamp());
        tmp.setWitness(Hex.toHexString(block.getBlockHeader().getRawData().getWitnessAddress().toByteArray()));

        for(Protocol.Transaction transaction: block.getTransactionsList()) {
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

          if (!transaction.getRet(0).toString().toLowerCase().contains("success")) {
            continue;
          }

          if(param != null) {
            Param p = Decode.decode(transaction);
            if(p.getPath().get(1).equals(param.getPath().get(1))){
              tmp.getTxs().add(transaction);
            }
          }

          if (!Hex.toHexString(capsule.getOwnerAddress()).toUpperCase().equals("41987C0191A1A098FFC9ADDC9C65D2C3D028B10CA3") ) {
            continue;
          }

          if(tmp.getLeftTx() == null) {
            tmp.setLeftTx(transaction);
            param = Decode.decode(transaction);
            continue;
          }

          if(tmp.getRightTx() == null) {
            tmp.setRightTx(transaction);
            break;
          }
        }

        int c1 = 0, c2 = 0, c3 = 0;
        if (tmp.getRightTx() != null) {
          c1++;
//          System.out.println((tmp.getRightTx() != null)
//            + ", " + (tmp.getLeftTx() != null) + ", " + tmp.getTxs().size());
//          System.out.println(tmp);

          if(tmp.getRightTx() != null && tmp.getLeftTx() != null && tmp.getTxs().size() > 0) {
            if(tmp.getTxs().size() == 1)c2++;
            results.add(tmp);
            String txid = new TransactionCapsule(tmp.getRightTx()).getTransactionId().toString();
            if(set.contains(txid)){
              logger.info("##### yes {}", txid);
            }else{
              logger.info("##### no {}，{}", txid, tmp.getTxs().size() == 1);
            }
            logger.info("{}", tmp);
          }
          tmp = new Result();
          param = null;

//          String url = "http://44.230.214.237:8090/wallet/record?value=" + new TransactionCapsule(tmp.getTxs().get(0)).getTransactionId();
//          System.out.println(url);
//          URLConnection urlConnection = new URL(url).openConnection();
//          BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
//          System.out.println(in.readLine());
        }

        logger.info("### c1 {}, c2 {}", c1, c2);

      }catch (Exception e) {

        System.out.println(e);
        e.printStackTrace();
      }

    }



  }
}
