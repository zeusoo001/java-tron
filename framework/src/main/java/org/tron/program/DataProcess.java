package org.tron.program;

import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.core.capsule.BytesCapsule;
import org.tron.core.db.CommonStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
public class DataProcess {

    @Setter
    private static CommonStore commonStore;

    public static void init(CommonStore store){
        commonStore = store;
        load();
    }

    public static void load() {

        try {
            for(int m = 0; m <= 3; m++) {
////            String fileName = "/Users/adiswu/git/develop-1/java-tron/file";
                String pr = "";
                if (m == 0) pr = "a";
                if (m == 0) pr = "b";
                if (m == 0) pr = "c";
                String path = "/data/trongrid-newlog/data/xa" + pr;
//
//                Scanner scanner = new Scanner(new File(path));
//                String s = scanner.nextLine();
//                while (scanner.hasNextLine()) {
//                    String tmp = scanner.nextLine();
//                    if (tmp.startsWith("api.fullnode")) {
//                        process(s);
//                        s = tmp;
//                    } else {
//                        s += tmp;
//                    }
//                }

                byte[] bytes = Files.readAllBytes(Paths.get(path));
                String content = new String(bytes, StandardCharsets.UTF_8);
                int index = 0;
                String[] sz = content.split("\n");
                for(int i = 1; i < sz.length; i++) {
                    if(sz[i].startsWith("api.fullnode")) {
                        String tmp = "";
                        for (int n = index; n < i; n++) {
                            tmp += sz[n];
                        }
                        process(tmp);
                        index = i;
                    }
                }
            }


            Map<String, Integer> map = new HashMap<>();
            ips.forEach((k, v) -> {
                commonStore.put(k.getBytes(), new BytesCapsule(JsonUtil.obj2Json(v).getBytes()));
                map.put(k, v.size());
            }) ;

            commonStore.put("all-ip".getBytes(), new BytesCapsule(JsonUtil.obj2Json(map).getBytes()));
        }catch (Exception e) {
            logger.error("{}", e.getMessage());
        }
    }
//

//    public static void load() {
//        try {
//            for(int m = 1; m <= 10; m++) {
////            String fileName = "/Users/adiswu/git/develop-1/java-tron/file";
//                String fileName = "/data/trongrid-newlog/data/a" + m;
//                byte[] bytes = Files.readAllBytes(Paths.get(fileName));
//                String content = new String(bytes, StandardCharsets.UTF_8);
//                int index = 0;
//                String[] sz = content.split("\n");
//                for(int i = 1; i < sz.length; i++) {
//                    if(sz[i].startsWith("api.fullnode")) {
//                        String tmp = "";
//                        for (int n = index; n < i; n++) {
//                            tmp += sz[n];
//                        }
//                        process(tmp);
//                        index = i;
//                    }
//                }
//            }
//            Map<String, Integer> map = new HashMap<>();
//            ips.forEach((k, v) -> {
//                commonStore.put(k.getBytes(), new BytesCapsule(JsonUtil.obj2Json(v).getBytes()));
//                map.put(k, v.size());
//            }) ;
//
//            commonStore.put("all-ip".getBytes(), new BytesCapsule(JsonUtil.obj2Json(map).getBytes()));
//        }catch (Exception e) {
//            logger.error("{}", e.getMessage());
//        }
//    }
////
    public static void main(String[] args) throws Exception {
        String fileName = "/Users/adiswu/git/develop-1/java-tron/file";
        byte[] bytes = Files.readAllBytes(Paths.get(fileName));
        String content = new String(bytes, StandardCharsets.UTF_8);
        String[] sz = content.split("\n");

        int index = 0;
        for(int i = 1; i < sz.length; i++) {
            if(sz[i].startsWith("api.fullnode")) {
                String tmp = "";
                for (int n = index; n < i; n++) {
                    tmp += sz[n];
                }
                process(tmp);
                index = i;
            }
        }
        System.out.println(ips);
    }

    @Data
    public static class TxData {
        private String txId;
        private String apiKey;
        private String ip;
        private String time;
        private String UA;
        private String serviceIp;
    }

    public static Map<String, Set<String>> ips = new HashMap<>();

    public static void process(String s) {
        TxData txData = get(s);
        if (txData == null || txData.getTxId().length() < 30) {
            return;
        }
        commonStore.put(txData.getTxId().getBytes(), new BytesCapsule(JsonUtil.obj2Json(txData).getBytes()));
        logger.info("key : {}", txData.getTxId());

        Set set = ips.get(txData.getIp());
        if (set == null) {
            set = new HashSet();
            ips.put(txData.getIp(), set);
        }
        set.add(txData.getTxId());
    }

    public static TxData get(String s) {
        TxData txData = null;
        try {
            String[] sz =  s.split(" ");
            if(sz.length == 26) {
                txData = new TxData();
                txData.setIp(sz[0].split(":")[1].trim());
                txData.setTime(sz[3].split("\\[19/Sep/")[1].split(" ")[0]);
                txData.setUA(sz[12].substring(1, sz[12].length() - 1));
                txData.setServiceIp(sz[13].substring(1, sz[13].length() - 1));
                txData.setApiKey(sz[18].substring(1, sz[18].length() - 1));
                txData.setTxId(sz[25].substring(0, sz[25].length() - 1));
            }
            if (sz.length == 27) {
                txData = new TxData();
                txData.setIp(sz[0].split(":")[1].trim());
                txData.setTime(sz[4].split("\\[19/Sep/")[1].split(" ")[0]);
                txData.setUA(sz[13].substring(1, sz[12].length() - 1));
                txData.setServiceIp(sz[14].substring(1, sz[13].length() - 1));
                txData.setApiKey(sz[19].substring(1, sz[18].length() - 1));
                txData.setTxId(sz[26].substring(0, sz[25].length() - 1));
            }
            if (sz.length == 28) {
                txData = new TxData();
                txData.setIp(sz[0].split(":")[1].trim());
                txData.setTime(sz[3].split("\\[19/Sep/")[1].split(" ")[0]);
                txData.setUA(sz[12].substring(1, sz[12].length() - 1));
                txData.setServiceIp(sz[15].substring(1, sz[13].length() - 1));
                txData.setApiKey(sz[20].substring(1, sz[18].length() - 1));
                txData.setTxId(sz[27].substring(0, sz[25].length() - 1));
            }
            return txData;
        }catch (Exception e) {
            logger.error("{}", e.getMessage());
            return null;
        }
    }
}
