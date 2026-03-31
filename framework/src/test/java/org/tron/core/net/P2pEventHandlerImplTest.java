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

    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);

    // second trx inv (100 items) dropped: count(10) + incoming(100) = 110 > maxCountIn10s(100)
    Assert.assertEquals(10, count);

    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.TRX);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);

    // third trx inv (100 items) dropped: count(10) + incoming(100) = 110 > maxCountIn10s(100)
    Assert.assertEquals(10, count);

    list.clear();
    for (int i = 0; i < 200; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);

    // first block inv (200 items) dropped: count(0) + incoming(200) = 200 > maxBlockInvCountIn10s(100)
    Assert.assertEquals(0, count);

    list.clear();
    for (int i = 0; i < 100; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }

    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);

    method.invoke(p2pEventHandler, peer, msg.getSendBytes());

    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);

    // second block inv (100 items) passes: count(0) + incoming(100) = 100, not > maxBlockInvCountIn10s(100)
    Assert.assertEquals(100, count);

  }

  @Test
  public void testBlockInvRateLimit() throws Exception {
    CommonParameter parameter = CommonParameter.getInstance();
    parameter.setMaxBlockInvCount(10);

    PeerStatistics peerStatistics = new PeerStatistics();
    PeerConnection peer = mock(PeerConnection.class);
    Mockito.when(peer.getPeerStatistics()).thenReturn(peerStatistics);

    P2pEventHandlerImpl p2pEventHandler = new P2pEventHandlerImpl();

    Method method = p2pEventHandler.getClass()
        .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
    method.setAccessible(true);

    List<Sha256Hash> list = new ArrayList<>();

    // first message: 50 items, count(0) + incoming(50) = 50, not > 100, passes
    for (int i = 0; i < 50; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    InventoryMessage msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    int count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);
    Assert.assertEquals(50, count);

    // second message: 60 items, count(50) + incoming(60) = 110 > 100, dropped
    list.clear();
    for (int i = 0; i < 60; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);
    Assert.assertEquals(50, count);

    // third message: 5 items, count(50) + incoming(5) = 55, not > 100, passes
    list.clear();
    for (int i = 0; i < 5; i++) {
      list.add(new Sha256Hash(i, new byte[32]));
    }
    msg = new InventoryMessage(list, Protocol.Inventory.InventoryType.BLOCK);
    method.invoke(p2pEventHandler, peer, msg.getSendBytes());
    count = peer.getPeerStatistics().messageStatistics.tronInBlockInventoryElement.getCount(10);
    Assert.assertEquals(55, count);
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
