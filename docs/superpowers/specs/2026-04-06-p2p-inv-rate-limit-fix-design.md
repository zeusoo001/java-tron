# P2P INV Rate Limit Fix & Block INV Rate Limiting — Design Spec

> **For agentic workers:** Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix the TRX INV rate limit bypass (current-batch size not included in check) and add symmetric BLOCK INV element-based rate limiting per peer.

**Architecture:** Two changes in `P2pEventHandlerImpl.processMessage()`, with a new config parameter for the BLOCK INV threshold. No new classes. All other infrastructure (`MessageStatistics`, `MessageCount`, `P2pRateLimiter`) is left untouched.

**Tech Stack:** Java, Guava RateLimiter (not used here — uses existing `MessageCount` window), Typesafe Config, Lombok.

---

## 1. Problem Analysis

### 1.1 TRX INV Bypass

`processMessage()` in `P2pEventHandlerImpl` checks the rate limit **before** calling `addTcpInMessage(msg)`:

```java
// Line 155-164: check (tronInTrxInventoryElement NOT yet updated for this msg)
int count = peer.getPeerStatistics().messageStatistics.tronInTrxInventoryElement.getCount(10);
if (inventoryType.equals(TRX) && count > maxCountIn10s) { return; }

// Line 167: only now does the counter get updated with this message's hash count
peer.getPeerStatistics().messageStatistics.addTcpInMessage(msg);
```

A peer can send one `InventoryMessage` carrying thousands of hashes. At check time `count = 0`, the message passes; `addTcpInMessage` then adds all hashes to the counter. The attack bypasses exactly one large batch per rate-limit window reset.

### 1.2 Missing BLOCK INV Rate Limit

The same `if (INVENTORY.equals(type))` block only gate-checks `TRX` inventory. `BLOCK` inventory messages have no per-peer rate limit at this level. `tronInBlockInventoryElement` is already tracked in `MessageStatistics` but never used for enforcement.

---

## 2. Design

### 2.1 Fix: Include Current Batch in TRX INV Check

**File:** `framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java`

Replace the existing rate-limit block (lines 152–165) with:

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
                logger.warn("[overload]Drop tx list is: {}",
                        ((InventoryMessage) msg).getHashList());
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

Key invariant: the check now uses `count + currentSize` so the current message's hashes are counted even before `addTcpInMessage` updates the window.

### 2.2 New Field: maxBlockInv10s

In `P2pEventHandlerImpl`, alongside the existing `maxCountIn10s`:

```java
private int maxCountIn10s   = Args.getInstance().getMaxTps() * 10;
private int maxBlockInv10s  = Args.getInstance().getMaxBlockInvPerPeer() * 10;
```

### 2.3 Config Parameter

**Default:** 10 block-hash elements per 10 seconds per peer (`maxBlockInv10s = 10 * 10 = 100`).

Rationale: normal block interval is 3 s → ~3.3 block hashes / 10 s from a well-behaved peer. A 3× headroom (10) is tight enough to catch abuse while still accommodating brief bursts from legitimate peers.

**`CommonParameter.java`** — add after `maxTps`:
```java
public int maxBlockInvPerPeer = 10; // clearParam: 10
```

**`ConfigKey.java`** — add after `NODE_MAX_TPS`:
```java
public static final String NODE_MAX_BLOCK_INV_PER_PEER = "node.maxBlockInvPerPeer";
```

**`Args.java`** — add after the `maxTps` loading line:
```java
PARAMETER.maxBlockInvPerPeer = config.hasPath(ConfigKey.NODE_MAX_BLOCK_INV_PER_PEER)
        ? config.getInt(ConfigKey.NODE_MAX_BLOCK_INV_PER_PEER) : 10;
```

**`config.conf`** — add a commented example in the `node { }` block near `# maxTps = 1000`:
```
# Maximum block-inventory hash elements accepted from one peer per 10 seconds.
# Default: 10  (≈ 3× normal block rate of ~3.3 hashes/10s)
# maxBlockInvPerPeer = 10
```

---

## 3. Files Changed

| File | Change |
|---|---|
| `framework/src/main/java/org/tron/core/net/P2pEventHandlerImpl.java` | Fix TRX INV check (add `currentSize`); add BLOCK INV check; add `maxBlockInv10s` field |
| `common/src/main/java/org/tron/common/parameter/CommonParameter.java` | Add `maxBlockInvPerPeer = 100` |
| `framework/src/main/java/org/tron/core/config/args/ConfigKey.java` | Add `NODE_MAX_BLOCK_INV_PER_PEER` |
| `framework/src/main/java/org/tron/core/config/args/Args.java` | Load `maxBlockInvPerPeer` from config |
| `framework/src/main/resources/config.conf` | Add commented `maxBlockInvPerPeer` example |

**Not changed:** `MessageStatistics.java`, `MessageCount.java`, `P2pRateLimiter.java`, `PeerConnection.java`.

---

## 4. Testing

### 4.1 Unit Test: `P2pEventHandlerImplInvRateLimitTest`

New test class at `framework/src/test/java/org/tron/core/net/P2pEventHandlerImplInvRateLimitTest.java`.

**TRX INV tests:**
- `testTrxInvSmallBatchPasses` — single message with `count < maxCountIn10s`, verify NOT dropped
- `testTrxInvLargeBatchBlocked` — single message with `currentSize > maxCountIn10s`, verify dropped (the bypass scenario)
- `testTrxInvCumulativeBlocked` — multiple small messages whose running sum exceeds the limit, verify drop triggers on the right message
- `testTrxInvExactLimitAllowed` — `count + currentSize == maxCountIn10s`, verify allowed (boundary)

**BLOCK INV tests:**
- `testBlockInvSmallBatchPasses` — single message with `count < maxBlockInv10s`, verify NOT dropped
- `testBlockInvLargeBatchBlocked` — single message with `currentSize > maxBlockInv10s`, verify dropped
- `testBlockInvCumulativeBlocked` — cumulative sum exceeds limit, verify drop
- `testBlockInvDefaultThreshold` — verify default `maxBlockInvPerPeer = 10` is loaded

**Test setup pattern:** `processMessage` is private. Change its visibility to package-private (`void processMessage(...)`) so tests in the same package can call it directly. Create a `PeerConnection` mock, inject a real `PeerStatistics`, prime `tronInTrxInventoryElement` / `tronInBlockInventoryElement` via `MessageCount.add(n)` before each call, then call `processMessage` directly and assert the method returns early (message NOT forwarded to `inventoryMsgHandler`) or proceeds normally.

---

## 5. Invariants & Edge Cases

| Case | Expected behavior |
|---|---|
| `currentSize = 0` (empty INV message) | `count + 0 > max` → only blocked if already over limit |
| `maxBlockInvPerPeer = 0` in config | `maxBlockInv10s = 0` → every BLOCK INV dropped; operator misconfiguration, documented |
| `InventoryType` is neither TRX nor BLOCK | Neither check fires; falls through to `inventoryMsgHandler` |
| `tronInBlockInventoryElement.getCount(10)` rolls over `Integer.MAX_VALUE` | `MessageCount` uses `int` accumulator per-second-slot; no overflow in window sum for practical values |
