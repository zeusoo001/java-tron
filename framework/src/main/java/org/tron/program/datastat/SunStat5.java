package org.tron.program.datastat;

import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.JsonUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j(topic = "Data")
public class SunStat5 {

  public static void init() throws Exception  {

    Set<String> set = new HashSet<>();
    String path = "/data/test/java-tron/f";
//    String path = "/Users/adiswu/git/develop-1/java-tron/f";
//
    String ssss = "e3fb78d36509e9395704237bac7583cc880b6007f06a1421ebf754d97bab241d";

    logger.info("path {}", path);

    byte[] bytes = Files.readAllBytes(Paths.get(path));

    String content = new String(bytes, StandardCharsets.UTF_8);

    String[] sz = content.split("\n");

    for(int i = 0; i < sz.length; i++) {
      set.add(sz[i].trim());
    }

    List<Result2> result2s = SunStat4.init();
    List<TrxDetail> details = SunStat3.init();

    result2s.forEach(r -> {
      if(set.contains(r.getLeftTx()) && set.contains(r.getRightTx())) {
        logger.info("{}", JsonUtil.obj2Json(r));
      } else {
        logger.info("## {}", JsonUtil.obj2Json(r));
      }
    });

    result2s.forEach(r -> {
      if(set.contains(r.getLeftTx()) && set.contains(r.getRightTx())) {
        logger.info("{}", r);
      } else {
        logger.info("@@ {}", r);
      }
    });
  }
}
