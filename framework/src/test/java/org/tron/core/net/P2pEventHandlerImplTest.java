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
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.Constant;
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
    Args.setParam(new String[] {"--output-directory", dbPath(), "--debug"}, Constant.TEST_CONF);
  }

  @Test
  public void testProcessInventoryMessage() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(10);

    PeerStatistics peerStatistics = new PeerStatistics();

    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();

    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    int count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement
            .getCount(10);

    Assert.assertEquals(0, count);

    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);

    Assert.assertEquals(10, count);

    // maxCountIn10s = maxTps(10) * 10 = 100; count(10) + size(100) = 110 > 100, should be dropped
    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);

    Assert.assertEquals(10, count);

    // count is still 10; count(10) + size(100) = 110 > 100, should also be dropped
    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);

    Assert.assertEquals(10, count);

    list.clear();
    for (int i = 0; i < 200; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);

    Assert.assertEquals(200, count);

    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);

    Assert.assertEquals(300, count);

  }

  @Test
  public void testProcessInventoryMessageSingleLargeBypass() throws Exception {
    // Verifies that a single oversized inventory message cannot bypass the rate limit.
    // A malicious node could previously send one message with 140k entries when count=0,
    // since the old check only tested count > maxCountIn10s (ignoring the current message size).
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxTps(10); // maxCountIn10s = 100

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();
    Method method = p2pEventHandler.getClass()
            .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    // count=0; single message with 140000 entries: 0 + 140000 = 140000 > 100, must be dropped
    List<Sha256Hash> list = new ArrayList<>();
    for (int i = 0; i < 140000; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    int count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);
    Assert.assertEquals(0, count);
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
