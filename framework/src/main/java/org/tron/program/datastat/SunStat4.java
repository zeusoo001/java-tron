package org.tron.program.datastat;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.api.WalletGrpc;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "Data")
public class SunStat4 {

  public static List<Result2> init() throws Exception {

    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("127.0.0.1:50051")
      .usePlaintext().build();
//    ManagedChannel channelFull2 = ManagedChannelBuilder.forTarget("52.196.244.176:50051")
//      .usePlaintext().build();

    WalletGrpc.WalletBlockingStub blockingStub = WalletGrpc.newBlockingStub(channelFull2);

    long startNum = 65697155;
    long endNum = 65899975;

    List<Result2> results = new ArrayList<>();

    Result2 tmp = new Result2();
    Param param = null;
    for(long i = startNum; i < endNum; i++) {
      try {
        GrpcAPI.NumberMessage msg = GrpcAPI.NumberMessage.newBuilder().setNum(i).build();
        Protocol.Block block = blockingStub.getBlockByNum(msg);

        tmp.setBlockNum(block.getBlockHeader().getRawData().getNumber());
        tmp.setBlockTime(SunStat2.get(block.getBlockHeader().getRawData().getTimestamp()));
        tmp.setWitness(StringUtil.encode58Check(block.getBlockHeader().getRawData().getWitnessAddress().toByteArray()));

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
              tmp.getTxs().add(new TransactionCapsule(transaction).getTransactionId().toString());
            }
          }

          if (!Hex.toHexString(capsule.getOwnerAddress()).toUpperCase().equals("41987C0191A1A098FFC9ADDC9C65D2C3D028B10CA3") ) {
            continue;
          }

          if(tmp.getLeftTx() == null) {
            tmp.setLeftTx(new TransactionCapsule(transaction).getTransactionId().toString());
            param = Decode.decode(transaction);
            continue;
          }

          if(tmp.getRightTx() == null) {
            tmp.setRightTx(new TransactionCapsule(transaction).getTransactionId().toString());
            break;
          }
        }

        int c1 = 0, c2 = 0, c3 = 0;
        if (tmp.getRightTx() != null) {
          c1++;
          if(tmp.getRightTx() != null && tmp.getLeftTx() != null && tmp.getTxs().size() > 0) {
            if(tmp.getTxs().size() == 1)c2++;
            results.add(tmp);
          }
          tmp = new Result2();
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

    return results;

  }
}
