package org.tron.core.net;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.tron.common.BaseTest;
import org.tron.common.TestConstants;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.config.args.Args;
import org.tron.core.net.message.TronMessage;
import org.tron.core.net.message.adv.FetchInvDataMessage;
import org.tron.core.net.message.adv.InventoryMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.net.service.statistics.PeerStatistics;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Inventory.InventoryType;

public class P2pEventHandlerImplTest extends BaseTest {

  @BeforeClass
  public static void init() throws Exception {
    Args.setParam(new String[] {"--output-directory", dbPath(), "--debug"},
        TestConstants.TEST_CONF);
  }

  @Test
  public void testProcessInventoryMessage() throws Exception {
    // maxTps=10 → maxCountIn10s=100; maxBlockInvPerPeer=10 → maxBlockInv10s=100
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(10);
    parameter.setMaxBlockInvPerPeer(10);

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // ── TRX INV: 10 hashes, count=0 → 0+10=10 ≤ 100, passes ──────────────
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals(10,
        peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));

    // ── TRX INV: 100 hashes, count=10 → 10+100=110 > 100, DROPPED ─────────
    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    // count must NOT increase — the 100-hash batch was dropped
    Assert.assertEquals(10,
        peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));

    // ── BLOCK INV: 10 hashes, count=0 → 0+10=10 ≤ 100, passes ────────────
    list.clear();
    for (int i = 0; i < 10; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals(10,
        peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));

    // ── BLOCK INV: 200 hashes, count=10 → 10+200=210 > 100, DROPPED ───────
    list.clear();
    for (int i = 0; i < 200; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    // count must NOT increase — the 200-hash batch was dropped
    Assert.assertEquals(10,
        peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));
  }

  /**
   * Regression test for the TRX INV bypass.
   * Before the fix, a single message with more hashes than maxCountIn10s would
   * pass the check (count=0 at check time) and all its hashes would be counted.
   * After the fix, count+currentSize > maxCountIn10s must drop the message.
   */
  @Test
  public void testTrxInvBypassScenario() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(1);  // maxCountIn10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // Single message with 100 hashes — well above the limit of 10
    // Before fix: count=0 at check time, passes, counter becomes 100.
    // After fix:  0+100=100 > 10, DROPPED, counter stays 0.
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    Assert.assertEquals("Large single TRX INV batch must be dropped",
        0, peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));
  }

  @Test
  public void testBlockInvLargeBatchBlocked() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxBlockInvPerPeer(1);  // maxBlockInv10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // 50 block hashes in one message → 0+50=50 > 10, must be dropped
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    Assert.assertEquals("Large single BLOCK INV batch must be dropped",
        0, peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));
  }

  @Test
  public void testBlockInvCumulativeBlocked() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxBlockInvPerPeer(1);  // maxBlockInv10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // First message: 8 hashes, count=0 → 0+8=8 ≤ 10, passes
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals(8,
        peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));

    // Second message: 5 hashes, count=8 → 8+5=13 > 10, DROPPED
    list.clear();
    for (int i = 0; i < 5; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals("Second BLOCK INV batch must be dropped when cumulative sum exceeds limit",
        8, peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));
  }

  @Test
  public void testTrxInvExactBoundaryAllowed() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(1);  // maxCountIn10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // Exactly 10 hashes → 0+10=10, NOT > 10, must pass
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals("Batch exactly at limit must be allowed",
        10, peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));
  }

  @Test
  public void testBlockInvExactBoundaryAllowed() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxBlockInvPerPeer(1);  // maxBlockInv10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // Exactly 10 block hashes → 0+10=10, NOT > 10, must pass
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals("Block batch exactly at limit must be allowed",
        10, peerStatistics.messageStatistics.tronInBlockInventoryElement.getCount(10));
  }

  @Test
  public void testTrxInvCumulativeBlocked() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(1);  // maxCountIn10s = 10

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // First message: 7 hashes, count=0 → 0+7=7 ≤ 10, passes
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals(7,
        peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));

    // Second message: 4 hashes, count=7 → 7+4=11 > 10, DROPPED
    list.clear();
    for (int i = 0; i < 4; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    Assert.assertEquals("Second TRX INV batch must be dropped when cumulative sum exceeds limit",
        7, peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10));
  }

  @Test
  public void testUpdateLastInteractiveTime() throws Exception {
    PeerConnection peer = new PeerConnection();
    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();

    Method method = p2pEventHandler.getClass()
        .getDeclaredMethod("updateLastInteractiveTime", PeerConnection.class, TronMessage.class);
    method.setAccessible(true);

    long t1 = System.currentTimeMillis();
    FetchInvDataMessage message = new FetchInvDataMessage(new ArrayList<>(), InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, message);
    Assert.assertTrue(peer.getLastInteractiveTime() >= t1);
  }
}
