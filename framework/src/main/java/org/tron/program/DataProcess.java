package org.tron.program;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.JsonUtil;
import org.tron.core.capsule.BytesCapsule;
import org.tron.core.db.CommonStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



@Slf4j(topic = "Data")
public class DataProcess {

    @Setter
    private static CommonStore commonStore;

    public static void init(CommonStore store){
        commonStore = store;
        load();
    }

    public static void load() {

        try {
            for(int kk = 1; kk <= 4; kk++) {
                String path = "/data/trongrid-newlog/data/" + kk;
//                String path = "/Users/adiswu/git/develop-1/java-tron/file";
//
                logger.info("path {}", path);

                byte[] bytes = Files.readAllBytes(Paths.get(path));

                logger.info("bytes {}", bytes.length);

                String content = new String(bytes, StandardCharsets.UTF_8);

                String[] sz = content.split("\n");

                logger.info("sz {}", sz.length);

                int index = 0;
                for(int i = 1; i < sz.length; i++) {
                    try {
                        if(sz[i].contains("api.fullnode") || sz[i].contains("api-limit.fullnode")) {
                            String tmp = "";
                            for (int n = index; n < i; n++) {
                                tmp += sz[n];
                            }
                            process(tmp);
                            index = i;
                            if(sz[i].length() > 10) {
                                logger.info("now process {}/{}", i, sz.length, sz[i].substring(0, 10));
                            }else {
                                logger.info("now process {}/{}", i, sz.length, sz[i]);
                            }

                        }
                    }catch (Exception e){
                        logger.info("process error 1 {}", e);
                    }
                    logger.info("now process {}/{}", i, sz.length);
                }

                logger.info("end process path {}", path);

            }

            logger.info("over process. ip-size {}", ips.size());

            Map<String, Integer> map = new HashMap<>();
            ips.forEach((k, v) -> {
                commonStore.put(k.getBytes(), new BytesCapsule(JsonUtil.obj2Json(v).getBytes()));
                map.put(k, v.size());
            }) ;

            commonStore.put("all-ip".getBytes(), new BytesCapsule(JsonUtil.obj2Json(map).getBytes()));
        }catch (Exception e) {
            logger.info("process error 2  {}", e);
        }
    }

    public static void main(String[] args) {
        load();
    }

    @Data
    public static class TxData {
        private String txId;
        private String apiKey;
        private String ip;
        private String time;
        private String UA;
    }

    public static Map<String, Set<String>> ips = new HashMap<>();

    public static void process(String s) {
        TxData txData = get(s);
        logger.info("{}", JsonUtil.obj2Json(txData));
        if (txData == null || txData.getTxId().length() != 64) {
            logger.info(s);
            return;
        }
        commonStore.put(txData.getTxId().getBytes(), new BytesCapsule(JsonUtil.obj2Json(txData).getBytes()));

        Set set = ips.get(txData.getIp());
        if (set == null) {
            set = new HashSet();
            ips.put(txData.getIp(), set);
        }
        set.add(txData.getTxId());

    }



    public static TxData get(String s) {

        try {
            if (s.contains("api.fullnode")) {
                TxData txData = new TxData();
                txData.setIp(s.split(" - - ")[0].split("gz:")[1]);
                txData.setTime(s.split("\\[")[1].split(" ")[0]);
                txData.setTxId(s.split("tx_id: ")[1].split("\"")[0]);
                txData.setApiKey(s.split("\"api.trongrid.io\" \"-\" \"")[1].split("\"")[0]);
                txData.setUA(s.split("api.trongrid.io")[0].split("\"-\" \"")[1].split("\"")[0]);
                return txData;
            } else if (s.contains("api-limit.fullnode")) {
                TxData txData = new TxData();
                txData.setIp(s.split(" - - ")[0].split("gz:")[1]);
                txData.setTime(s.split("\\[")[1].split(" ")[0]);
                txData.setTxId(s.split("tx_id: ")[1].split("\"")[0]);
                return txData;
            }else {
                logger.info(" onxxxx {} ", s.substring(0, 20));
            }
        } catch (Exception e) {
            logger.info("process error 3 {}", e);
        }
        return null;
    }
}
