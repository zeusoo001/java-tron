package org.tron.program;

import lombok.Data;
import org.tron.core.capsule.TransactionCapsule;

@Data
public class XXX {
  private TransactionCapsule c1;
  private TransactionCapsule c2;

  public XXX(TransactionCapsule c1, TransactionCapsule c2) {
    this.c1 = c1;
    this.c2 = c2;
  }
}
