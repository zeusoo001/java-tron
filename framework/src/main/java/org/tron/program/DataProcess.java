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
                        if(sz[i] != null && sz[i].contains("api.fullnode")) {
                            String tmp = "";
                            for (int n = index; n < i; n++) {
                                tmp += sz[n];
                            }
                            process(tmp);
                            index = i;
                        }
                    }catch (Exception e){
                        logger.info("process error 1 {}", e);
                    }
                }

                logger.info("over process. ip-size {}", ips.size());

                Map<String, Integer> map = new HashMap<>();
                ips.forEach((k, v) -> {
                    commonStore.put(k.getBytes(), new BytesCapsule(JsonUtil.obj2Json(v).getBytes()));
                    map.put(k, v.size());
                }) ;

                commonStore.put("all-ip".getBytes(), new BytesCapsule(JsonUtil.obj2Json(map).getBytes()));
            }

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
//        a1:api.fullnode.access.log-20240920.gz:13.209.18.131 - - [19/Sep/2024:02:48:48 +0000] "POST /wallet/broadcasttransaction HTTP/1.1" 200 90 0.008 "-" "axios/0.21.4" "34.142.
//        136.208:8090" "200" "0.007" "api.trongrid.io" "-" "01fdd4b3-7212-43a7-8302-7253911cb061" "1" "-" "{\x22visible\x22:false,\x22txID\x22:\x2277a142681cb97d98bce112ac7cfba77ba
//            33aed789892565f06744270f5303823\x22,\x22raw_data\x22:{\x22contract\x22:[{\x22parameter\x22:{\x22value\x22:{\x22data\x22:\x22a9059cbb000000000000000000000000bdd2974950f2b93
//                788e5b1ac2117d189c08de64b0000000000000000000000000000000000000000000000000000000005f5e100\x22,\x22owner_address\x22:\x2241393ae652f6b407f61b09873536dc92d7282d0cde\x22,\x22
//                contract_address\x22:\x2241a614f803b6fd780986a42c78ec9c7f77e6ded13c\x22},\x22type_url\x22:\x22type.googleapis.com/protocol.TriggerSmartContract\x22},\x22type\x22:\x22Trigg
//                erSmartContract\x22}],\x22ref_block_bytes\x22:\x223e2a\x22,\x22ref_block_hash\x22:\x22916cae4febbf0dca\x22,\x22expiration\x22:1726714188000,\x22fee_limit\x22:150000000,\x2
//                2timestamp\x22:1726714128697},\x22raw_data_hex\x22:\x220a023e2a2208916cae4febbf0dca40e0a1bec1a0325aae01081f12a9010a31747970652e676f6f676c65617069732e636f6d2f70726f746f636f
//            6c2e54726967676572536d617274436f6e747261637412740a1541393ae652f6b407f61b09873536dc92d7282d0cde121541a614f803b6fd780986a42c78ec9c7f77e6ded13c2244a9059cbb0000000000000000000
//            00000bdd2974950f2b93788e5b1ac2117d189c08de64b0000000000000000000000000000000000000000000000000000000005f5e10070b9d2bac1a032900180a3c347\x22,\x22signature\x22:[\x2205bdace0
//            5bb4e04e57c6b43e3780f686f5aa2831a5f42b96a714769abd566373864b198fb87f061564a7940384de33b00c994a852b1bea9819d5ca1fc523ccf800\x22]}"upstream: apikey-api  "tx_id: 77a142681cb9
//        7d98bce112ac7cfba77ba33aed789892565f06744270f5303823"


        try {
            TxData txData = new TxData();
            txData.setIp(s.split(" - - ")[0].split("gz:")[1]);
            txData.setTime(s.split("\\[")[1].split(" ")[0]);
            txData.setTxId(s.split("tx_id: ")[1].split("\"")[0]);
            txData.setApiKey(s.split("\"api.trongrid.io\" \"-\" \"")[1].split("\"")[0]);
            txData.setUA(s.split("api.trongrid.io")[0].split("\"-\" \"")[1].split("\"")[0]);
//            String[] sz =  s.split(" ");
//            System.out.println(sz.length);
//            for(int i = 0; i< sz.length; i++) {
//                System.out.println(i + ", " + sz[i]);
//            }
//            if(sz.length == 26) {
//                txData = new TxData();
//                txData.setIp(sz[0].split(":")[2].trim());
//                txData.setTime(sz[3].split("\\[19/Sep/")[1].split(" ")[0]);
//                txData.setUA(sz[12].substring(1, sz[12].length() - 1));
//                txData.setServiceIp(sz[13].substring(1, sz[13].length() - 1));
//                txData.setApiKey(sz[18].substring(1, sz[18].length() - 1));
//                txData.setTxId(sz[25].substring(0, sz[25].length() - 1));
//            }
//            if (sz.length == 27) {
//                txData = new TxData();
//                txData.setIp(sz[0].split(":")[2].trim());
//                txData.setTime(sz[4].split("\\[19/Sep/")[1].split(" ")[0]);
//                txData.setUA(sz[13].substring(1, sz[12].length() - 1));
//                txData.setServiceIp(sz[14].substring(1, sz[13].length() - 1));
//                txData.setApiKey(sz[19].substring(1, sz[18].length() - 1));
//                txData.setTxId(sz[26].substring(0, sz[25].length() - 1));
//            }
//            if (sz.length == 28) {
//                txData = new TxData();
//                txData.setIp(sz[0].split(":")[2].trim());
//                txData.setTime(sz[3].split("\\[19/Sep/")[1].split(" ")[0]);
//                txData.setUA(sz[12].substring(1, sz[12].length() - 1));
//                txData.setServiceIp(sz[15].substring(1, sz[13].length() - 1));
//                txData.setApiKey(sz[20].substring(1, sz[18].length() - 1));
//                txData.setTxId(sz[27].substring(0, sz[25].length() - 1));
//            }
            return txData;
        }catch (Exception e) {
            logger.info("process error 3 {}", e);
            return null;
        }
    }
}
