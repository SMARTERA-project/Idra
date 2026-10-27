package it.eng.idra.utils.restclient;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrokerManagerResponseTest {
  @Test void failedOrPartialDeletionStopsBeforeChangingLocalCatalogue() throws Exception {
    RestClient client = new RestClientImpl();
    for (int status : new int[]{207, 301, 400, 502}) {
      org.apache.http.message.BasicHttpResponse response = new org.apache.http.message.BasicHttpResponse(
          new org.apache.http.ProtocolVersion("HTTP", 1, 1), status, "test");
      it.eng.idra.exception.AppException error = assertThrows(it.eng.idra.exception.AppException.class,
          () -> BrokerManagerResponse.requireDeletionSuccess(client, response));
      assertEquals(it.eng.idra.exception.ErrorCode.ERR_UPSTREAM_UNAVAILABLE, error.getErrorCode());
    }
    assertThrows(it.eng.idra.exception.AppException.class, () -> BrokerManagerResponse.requireDeletionSuccess(client, null));
    assertEquals(204, BrokerManagerResponse.requireDeletionSuccess(client,
        new org.apache.http.message.BasicHttpResponse(new org.apache.http.ProtocolVersion("HTTP", 1, 1), 204, "No Content")));
  }
  @Test void handlesEmptySuccessErrorsAndLegacyBodiesOverHttp() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      exchange.getRequestBody().readAllBytes();
      String path = exchange.getRequestURI().getPath();
      int status = path.equals("/empty") ? 204 : path.equals("/failure") ? 502 : 200;
      byte[] body = (path.equals("/legacy") ? "{\"status\":204}" : path.equals("/failure") ? "{\"status\":204}" : "").getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
      if (body.length > 0) exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      for (String path : new String[]{"/empty", "/legacy", "/ok", "/failure"}) {
        RestClient client = new RestClientImpl();
        assertEquals(path.equals("/failure") ? 502 : path.equals("/ok") ? 200 : 204,
            BrokerManagerResponse.status(client, client.sendPostRequest(base + path, "{}", MediaType.APPLICATION_JSON_TYPE, Collections.emptyMap())));
      }
      assertEquals(-1, BrokerManagerResponse.status(new RestClientImpl(), null));
    } finally { server.stop(0); }
  }
}
