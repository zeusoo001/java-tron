package org.tron.program;

import com.google.protobuf.ByteString;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.crypto.SignInterface;
import org.tron.common.crypto.SignUtils;
import org.tron.common.es.ExecutorServiceManager;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.StringUtil;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.net.message.adv.TransactionMessage;
import org.tron.core.net.service.adv.AdvService;
import org.tron.protos.Protocol;
import org.tron.protos.contract.BalanceContract;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "BT")
public class Broadcast {

  public static String sourcePri = "c96c92c8a5f68ffba2ced3f7cd4baa6b784838a366f62914efdc79c6c18cd7d0";

  public static byte[] sourcePub = SignUtils.fromPrivate(ByteArray.fromHexString(sourcePri), true).getAddress();

  public static String desPri = "c96c92c8a5f68ffba2ced3f7cd4baa6b784838a366f62914efdc79c6c18cd7d1";

  public static byte[] desPub = SignUtils.fromPrivate(ByteArray.fromHexString(desPri), true).getAddress();

  @Setter
  public static Manager manager;

  @Setter
  public static AdvService advService;

  public static Map<Integer, Integer> map = new HashMap<>();

  public static Map<Integer, List<XXX>> result = new HashMap<>();

  public static Map<Sha256Hash, TransactionCapsule> tmpTx = new ConcurrentHashMap<>();

  public static int loopCnt = 1000;

  static {
    map.put(1, 5);
    map.put(2, 10);
    map.put(3, 30);
    map.put(4, 50);
    map.put(5, 100);
    map.put(6, 150);
    map.put(7, 200);
    map.put(8, 300);
    map.put(9, 500);
    map.put(10, 1000);
  }

  public static void f() {
    if (!Args.getInstance().isBroadcast()) {
      return;
    }
    for (int i = 1; i <= 10; i++) {
      for(int j = 0; j < loopCnt; j++) {
        TransactionCapsule c1 = getTx();
        TransactionCapsule c2 = getTx();

        List<XXX> list = result.get(i);
        if (list == null) {
          list = new ArrayList<>();
          result.put(i, list);
        }
        list.add(new XXX(c1, c2));

        tmpTx.put(c1.getTransactionId(), c1);
        tmpTx.put(c2.getTransactionId(), c2);

        advService.broadcast(new TransactionMessage(c1.getInstance()));
        try{ Thread.sleep(map.get(i));}catch (Exception e){}
        advService.broadcast(new TransactionMessage(c1.getInstance()));

        try{ Thread.sleep(new Random().nextInt(1000));}catch (Exception e){}
      }

      try{ Thread.sleep(10_000);}catch (Exception e){}
      stat(i);
    }
  }

  public static void stat(int i) {
    List<XXX> list = result.get(i);
    int cnt = 0;
    for(XXX x: list) {
      if (x.getC1().getBlockNum() <= 0 || x.getC2().getBlockNum() <= 0) {
        continue;
      }
      if (x.getC1().getBlockNum() > x.getC2().getBlockNum()) {
        cnt++;
      } else if (x.getC1().getBlockNum() == x.getC2().getBlockNum()) {
        if (x.getC1().getBlockIndex() > x.getC2().getBlockIndex()) {
          cnt++;
        }
      }
    }
    logger.info("###@@@ {}ms, {}, {}", map.get(i), cnt, list.size());
  }

  public static void rcvBlock(BlockCapsule blockCapsule) {
    tmpTx.forEach((k, v) -> {
      int i = 0;
      for(TransactionCapsule capsule: blockCapsule.getTransactions()) {
        if (k.equals(capsule.getTransactionId())) {
          v.setBlockNum(blockCapsule.getNum());
          v.setBlockIndex(i);
          tmpTx.remove(k);
        }
        i++;
      }
    });
  }

  public static void main(String[] args) {
    String sourcePri = "c96c92c8a5f68ffba2ced3f7cd4baa6b784838a366f62914efdc79c6c18cd7d1";

    byte[] sourcePub = SignUtils.fromPrivate(ByteArray.fromHexString(sourcePri), true).getAddress();

    System.out.println(StringUtil.encode58Check(sourcePub));

  }

  public static final ScheduledExecutorService executor = ExecutorServiceManager
    .newSingleThreadScheduledExecutor("fetchName");

  public static void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
//        broadcastTx();
        f();
      } catch (Exception exception) {
        logger.error("Spread thread error", exception);
      }
    }, 60, 3600, TimeUnit.SECONDS);
  }

  public static void broadcastTx() {
    if (!Args.getInstance().isBroadcast()) {
      return;
    }
    TransactionCapsule capsule = getTx();
    advService.broadcast(new TransactionMessage(capsule.getInstance()));
  }

  public static void rcvTx(TransactionCapsule c1) {
    if (!Args.getInstance().isAttack()) {
      return;
    }
    TransactionCapsule c2 = getTx();
    logger.info("#### rcvTx,{}, advTx,{}", c1.getTransactionId(), c2.getTransactionId());
    advService.broadcast(new TransactionMessage(c2.getInstance()));
  }

  private static TransactionCapsule getTx() {
    BalanceContract.TransferContract contract = BalanceContract.TransferContract.newBuilder()
      .setAmount(new Random().nextInt(10000))
      .setOwnerAddress(ByteString.copyFrom(sourcePub))
      .setToAddress(ByteString.copyFrom(desPub)).build();

    TransactionCapsule capsule = new TransactionCapsule(contract, Protocol.Transaction.Contract.ContractType.TransferContract);

    Protocol.Transaction.Builder trxBuilder = capsule.getInstance().toBuilder();

    Protocol.Transaction.raw.Builder rawBuilder = capsule.getInstance().getRawData().toBuilder()
      .setExpiration(System.currentTimeMillis() + 3600 * 1000 + new Random().nextInt(10000))
      .setFeeLimit(50_000_000);

    trxBuilder.setRawData(rawBuilder);

    Protocol.Transaction transaction = trxBuilder.build();

    TransactionCapsule c2 = new TransactionCapsule(transaction);

    c2.setReference(manager.getDynamicPropertiesStore().getLatestBlockHeaderNumber(),
      manager.getDynamicPropertiesStore().getLatestBlockHeaderHash().getBytes());

    SignInterface cryptoEngine = SignUtils
      .fromPrivate(Hex.decode(sourcePri), CommonParameter.getInstance().isECKeyCryptoEngine());

    Sha256Hash hash = Sha256Hash.of(true, c2.getInstance().getRawData().toByteArray());

    byte[] bytes = cryptoEngine.Base64toBytes(cryptoEngine.signHash(hash.getBytes()));

    ByteString sig = ByteString.copyFrom(bytes);

    transaction = c2.getInstance().toBuilder().addSignature(sig).build();

    return new TransactionCapsule(transaction);
  }


}
