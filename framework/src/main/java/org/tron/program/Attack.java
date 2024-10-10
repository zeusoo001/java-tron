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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "BT")
public class Attack {

  public static String sourcePri = "c96c92c8a5f68ffba2ced3f7cd4baa6b784838a366f62914efdc79c6c18cd7d0";

  public static byte[] sourcePub = SignUtils.fromPrivate(ByteArray.fromHexString(sourcePri), true).getAddress();

  public static String desPri = "c96c92c8a5f68ffba2ced3f7cd4baa6b784838a366f62914efdc79c6c18cd7d1";

  public static byte[] desPub = SignUtils.fromPrivate(ByteArray.fromHexString(desPri), true).getAddress();

  @Setter
  public static Manager manager;

  @Setter
  public static AdvService advService;


  public static List<XXX> result = new CopyOnWriteArrayList<>();

  public static Map<Sha256Hash, TransactionCapsule> tmpTx = new ConcurrentHashMap<>();

  public static int loopCnt = 1000;

  public static final ScheduledExecutorService executor = ExecutorServiceManager
    .newSingleThreadScheduledExecutor("attack");

  public static void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        stat();
      } catch (Exception exception) {
        logger.error("Spread thread error", exception);
      }
    }, 60, 1, TimeUnit.SECONDS);
  }


  public static void stat() throws Exception {
    if (result.size() < loopCnt) {
      return;
    }

    Thread.sleep(10_000);

    int cnt = 0;
    int offChain = 0;

    List<XXX> list = new ArrayList(result);
    tmpTx.clear();
    result.clear();


    for(XXX x: list) {
      if (x.getC1().getBlockNum() <= 0 || x.getC2().getBlockNum() <= 0) {
        offChain++;
        logger.info("### txId {}, {}, blockNum {}, {}",
          x.getC1().getTransactionId(), x.getC2().getTransactionId(), x.getC1().getBlockNum(), x.getC2().getBlockNum());
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
    logger.info("###@@@ {}, {}, {}",  cnt, offChain, list.size());
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

  public static void rcvTx(TransactionCapsule c1) {
    if (!Args.getInstance().isAttack()) {
      return;
    }

    if (result.size() >= loopCnt) {
      return;
    }

    TransactionCapsule c2 = getTx();

    tmpTx.put(c1.getTransactionId(), c1);
    tmpTx.put(c2.getTransactionId(), c2);

    XXX x = new XXX(c1, c2);
    result.add(x);

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
