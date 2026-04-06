# P2P INV Rate Limit Fix & Block INV Rate Limiting — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the TRX INV rate-limit bypass (current-batch size excluded from check) and add symmetric element-based BLOCK INV rate limiting per peer.

**Architecture:** All changes are in `P2pEventHandlerImpl.processMessage()` plus 4 supporting config files. The existing `MessageCount` sliding-window counters (`tronInTrxInventoryElement`, `tronInBlockInventoryElement`) are reused as-is. No new classes.

**Tech Stack:** Java 8+, Typesafe Config (HOCON), Lombok `@Getter @Setter`, JUnit 4, Mockito, existing `CommonParameter` / `ConfigKey` / `Args` config pattern.

---

## File Map

| File | Role |
|---|---|
| `framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java` | Fix TRX INV check; add BLOCK INV check; add `maxBlockInv10s` field |
| `common/src/main/java/org/tron/common/parameter/CommonParameter.java` | Add `maxBlockInvPerPeer` field (default 10) |
| `framework/src/main/java/org/tron/core/config/args/ConfigKey.java` | Add `NODE_MAX_BLOCK_INV_PER_PEER` constant |
| `framework/src/main/java/org/tron/core/config/args/Args.java` | Load `maxBlockInvPerPeer` from HOCON config |
| `framework/src/main/resources/config.conf` | Add commented `maxBlockInvPerPeer` example |
| `framework/src/test/java/org/tron/core/net/P2pEventHandlerImplTest.java` | Update existing test + add new targeted tests |

---

## Task 1: Config — add maxBlockInvPerPeer parameter

**Files:**
- Modify: `common/src/main/java/org/tron/common/parameter/CommonParameter.java:119`
- Modify: `framework/src/main/java/org/tron/core/config/args/ConfigKey.java:78`
- Modify: `framework/src/main/java/org/tron/core/config/args/Args.java:401`
- Modify: `framework/src/main/resources/config.conf:191`

- [ ] **Step 1: Add field to CommonParameter**

Open `common/src/main/java/org/tron/common/parameter/CommonParameter.java`.

After line 119 (`public int maxTps; // clearParam: 1000`), insert:

```java
  @Getter
  @Setter
  public int maxBlockInvPerPeer = 10; // clearParam: 10
```

- [ ] **Step 2: Add config key to ConfigKey**

Open `framework/src/main/java/org/tron/core/config/args/ConfigKey.java`.

After line 78 (`public static final String NODE_MAX_TPS = "node.maxTps";`), insert:

```java
  public static final String NODE_MAX_BLOCK_INV_PER_PEER = "node.maxBlockInvPerPeer";
```

- [ ] **Step 3: Load from config in Args**

Open `framework/src/main/java/org/tron/core/config/args/Args.java`.

After lines 401–402:
```java
    PARAMETER.maxTps = config.hasPath(ConfigKey.NODE_MAX_TPS)
            ? config.getInt(ConfigKey.NODE_MAX_TPS) : 1000;
```

Insert:
```java
    PARAMETER.maxBlockInvPerPeer = config.hasPath(ConfigKey.NODE_MAX_BLOCK_INV_PER_PEER)
            ? config.getInt(ConfigKey.NODE_MAX_BLOCK_INV_PER_PEER) : 10;
```

- [ ] **Step 4: Add comment in config.conf**

Open `framework/src/main/resources/config.conf`.

After line 191 (`# maxTps = 1000`), insert a blank line then:

```
  # Maximum block-inventory hash elements accepted from one peer per 10 seconds.
  # Default: 10  (≈ 3× normal block rate of ~3.3 hashes/10s)
  # maxBlockInvPerPeer = 10
```

- [ ] **Step 5: Compile to verify**

```bash
./gradlew :common:compileJava :framework:compileJava -q
```

Expected: BUILD SUCCESSFUL, no errors.

- [ ] **Step 6: Commit**

```bash
git add \
  common/src/main/java/org/tron/common/parameter/CommonParameter.java \
  framework/src/main/java/org/tron/core/config/args/ConfigKey.java \
  framework/src/main/java/org/tron/core/config/args/Args.java \
  framework/src/main/resources/config.conf
git commit -m "feat(p2p): add maxBlockInvPerPeer config parameter (default 10)"
```

---

## Task 2: Write failing tests

**Files:**
- Modify: `framework/src/test/java/org/tron/core/net/P2pEventHandlerImplTest.java`

### Background: how the existing test works

The test uses reflection to call the private `processMessage(PeerConnection, byte[])` method:
```java
Method method = p2pEventHandler.getClass()
        .getDeclaredMethod("processMessage", PeerConnection.class, byte[].class);
method.setAccessible(true);
method.invoke(p2pEventHandler, peer, msg.getSendBytes());
```

`PeerConnection` is a Mockito mock that returns a real `PeerStatistics` object. The test inspects `peerStatistics.messageStatistics.tronInTrxInventoryElement.getCount(10)` to determine whether the message was processed (count increased) or dropped (count stayed the same).

### What to change in the existing test

The existing `testProcessInventoryMessage` is no longer correct after the fix. Its current assertions assume the old (buggy) behavior:
- It sends 10 TRX hashes, then 100 TRX hashes and **expects both to pass** (count 10 → 110).
- After the fix, the 2nd send (10+100=110 > 100) must be **dropped** (count stays at 10).
- It sends 200 BLOCK hashes and **expects them to pass** (blockCount=200).
- After the fix, the BLOCK INV check will drop that message (0+200=200 > 100).

Replace the entire `testProcessInventoryMessage` with the corrected version and add new targeted tests below it.

- [ ] **Step 1: Replace testProcessInventoryMessage**

Replace the entire method body of `testProcessInventoryMessage` in
`framework/src/test/java/org/tron/core/net/P2pEventHandlerImplTest.java`:

```java
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
```

- [ ] **Step 2: Add testTrxInvBypassScenario — the specific bug being fixed**

Add this new test after `testProcessInventoryMessage`:

```java
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
```

- [ ] **Step 3: Add testBlockInvLargeBatchBlocked**

```java
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
```

- [ ] **Step 4: Add testBlockInvCumulativeBlocked**

```java
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
```

- [ ] **Step 5: Add testTrxInvExactBoundaryAllowed**

```java
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
```

- [ ] **Step 6: Run tests — they must FAIL (implementation not changed yet)**

```bash
./gradlew :framework:test \
  --tests 'org.tron.core.net.P2pEventHandlerImplTest' \
  --info 2>&1 | grep -E "PASS|FAIL|ERROR|tests were"
```

Expected: several tests FAIL (because `processMessage` still uses the old logic).

---

## Task 3: Implement the fix in P2pEventHandlerImpl

**Files:**
- Modify: `framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java`

- [ ] **Step 1: Add maxBlockInv10s field**

Open `framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java`.

After line 93:
```java
  private int maxCountIn10s = Args.getInstance().getMaxTps() * 10;
```

Insert:
```java
  private int maxBlockInv10s = Args.getInstance().getMaxBlockInvPerPeer() * 10;
```

- [ ] **Step 2: Replace the INV rate-limit block**

In `processMessage`, find and replace this existing block (lines 152–165):

```java
      if (INVENTORY.equals(type)) {
        InventoryMessage message = (InventoryMessage) msg;
        Protocol.Inventory.InventoryType inventoryType = message.getInventoryType();
        int count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement
                .getCount(10);
        if (inventoryType.equals(Protocol.Inventory.InventoryType.TRX) && count > maxCountIn10s) {
          logger.warn("Drop inventory from Peer {}, cur:{}, max:{}",
                  peer.getInetAddress(), count, maxCountIn10s);
          if (Args.getInstance().isOpenPrintLog()) {
            logger.warn("[overload]Drop tx list is: {}", ((InventoryMessage) msg).getHashList());
          }
          return;
        }
      }
```

Replace with:

```java
      if (INVENTORY.equals(type)) {
        InventoryMessage message = (InventoryMessage) msg;
        Protocol.Inventory.InventoryType inventoryType = message.getInventoryType();
        int currentSize = message.getInventory().getIdsCount();

        if (inventoryType.equals(Protocol.Inventory.InventoryType.TRX)) {
          int count = peer.getPeerStatistics().messageStatistics
                  .tronInTrxInventoryElement.getCount(10);
          if (count + currentSize > maxCountIn10s) {
            logger.warn("Drop trx inventory from Peer {}, count:{}, batch:{}, max:{}",
                    peer.getInetAddress(), count, currentSize, maxCountIn10s);
            if (Args.getInstance().isOpenPrintLog()) {
              logger.warn("[overload]Drop tx list is: {}", message.getHashList());
            }
            return;
          }
        }

        if (inventoryType.equals(Protocol.Inventory.InventoryType.BLOCK)) {
          int count = peer.getPeerStatistics().messageStatistics
                  .tronInBlockInventoryElement.getCount(10);
          if (count + currentSize > maxBlockInv10s) {
            logger.warn("Drop block inventory from Peer {}, count:{}, batch:{}, max:{}",
                    peer.getInetAddress(), count, currentSize, maxBlockInv10s);
            return;
          }
        }
      }
```

- [ ] **Step 3: Compile**

```bash
./gradlew :framework:compileJava -q
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all tests — they must PASS**

```bash
./gradlew :framework:test \
  --tests 'org.tron.core.net.P2pEventHandlerImplTest' \
  --info 2>&1 | grep -E "PASS|FAIL|ERROR|tests were"
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add \
  framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java \
  framework/src/test/java/org/tron/core/net/P2pEventHandlerImplTest.java
git commit -m "fix(p2p): include current INV batch size in TRX rate check; add BLOCK INV rate limit"
```

---

## Self-Review Checklist

**Spec coverage:**
- §2.1 TRX INV fix (`count + currentSize`) → Task 3 Step 2 ✓
- §2.2 `maxBlockInv10s` field → Task 3 Step 1 ✓
- §2.3 `CommonParameter.maxBlockInvPerPeer` → Task 1 Step 1 ✓
- §2.3 `ConfigKey.NODE_MAX_BLOCK_INV_PER_PEER` → Task 1 Step 2 ✓
- §2.3 `Args` loading → Task 1 Step 3 ✓
- §2.3 `config.conf` comment → Task 1 Step 4 ✓
- §4 Tests (bypass, cumulative, boundary, block large, block cumulative) → Task 2 ✓
- §5 Boundary `count + currentSize == max` allows → `testTrxInvExactBoundaryAllowed` ✓

**Placeholder scan:** No TBD/TODO. All code blocks are complete and self-contained.

**Type consistency:**
- `getMaxBlockInvPerPeer()` — generated by Lombok `@Getter` on `CommonParameter.maxBlockInvPerPeer`. Used in Task 1 (field def) and Task 3 Step 1 (consumer). ✓
- `maxBlockInv10s` — defined in Task 3 Step 1, used in Task 3 Step 2. ✓
- `tronInBlockInventoryElement` — existing field on `MessageStatistics`, no changes needed. ✓
