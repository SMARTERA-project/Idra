package it.eng.idra.dcat.dump;

import com.sun.net.httpserver.HttpServer;
import it.eng.idra.utils.NgsiDcatSubscription;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NgsiSubscriptionTest {
  @Test void upgradesExistingTitleOnlySubscriptionAndPreservesDeliveryEndpoint() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    AtomicReference<JSONObject> patch=new AtomicReference<>();
    server.createContext("/ngsi-ld/v1/subscriptions/",exchange->{
      if("GET".equals(exchange.getRequestMethod())) {
        byte[] body="{\"notification\":{\"attributes\":[\"title\"],\"endpoint\":{\"uri\":\"http://existing/callback\",\"accept\":\"application/json\"}}}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
      } else {
        assertEquals("PATCH",exchange.getRequestMethod());
        patch.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
        exchange.sendResponseHeaders(204,-1);
      }
      exchange.close();
    });server.start();
    try {
      NgsiDcatSubscription.ensure("http://127.0.0.1:"+server.getAddress().getPort(),999999,"http://new/callback");
      assertFalse(patch.get().getJSONObject("notification").has("attributes"));
      assertEquals("http://existing/callback",patch.get().getJSONObject("notification").getJSONObject("endpoint").getString("uri"));
      assertTrue(patch.get().getJSONArray("entities").toString().contains("http://www.w3.org/ns/dcat#DatasetSeries"));
      assertFalse(patch.get().has("id"));
    } finally {server.stop(0);}
  }
}
