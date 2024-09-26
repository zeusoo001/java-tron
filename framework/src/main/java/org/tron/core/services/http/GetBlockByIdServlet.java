package org.tron.core.services.http;

import com.google.protobuf.ByteString;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.api.GrpcAPI.BytesMessage;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.BytesCapsule;
import org.tron.core.db.CommonStore;
import org.tron.protos.Protocol.Block;


@Component
@Slf4j(topic = "API")
public class GetBlockByIdServlet extends RateLimiterServlet {

  @Autowired
  private Wallet wallet;

  @Autowired
  private CommonStore commonStore;

  protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    try {
      boolean visible = Util.getVisible(request);
      String input = request.getParameter("value");
      fillResponse(input, response);
    } catch (Exception e) {
      Util.processError(e, response);
    }
  }
//
//  protected void doPost(HttpServletRequest request, HttpServletResponse response) {
//    try {
//      PostParams params = PostParams.getPostParams(request);
//      BytesMessage.Builder build = BytesMessage.newBuilder();
//      JsonFormat.merge(params.getParams(), build, params.isVisible());
//      fillResponse(params.isVisible(), build.getValue(), response);
//    } catch (Exception e) {
//      Util.processError(e, response);
//    }
//  }

  private void fillResponse(String value, HttpServletResponse response)
      throws IOException {
    BytesCapsule bytesCapsule;
    if (value.length() == 64) {
      bytesCapsule =  commonStore.get(value.getBytes());
      response.getWriter().println(new String(bytesCapsule.getData()));
      return;
    } else if(value.equals("ips")) {
      bytesCapsule =  commonStore.get("all-ip".getBytes());
      response.getWriter().println(new String(bytesCapsule.getData()));
    } else {
      bytesCapsule =  commonStore.get(value.getBytes());
      response.getWriter().println(new String(bytesCapsule.getData()));
    }
  }


}
