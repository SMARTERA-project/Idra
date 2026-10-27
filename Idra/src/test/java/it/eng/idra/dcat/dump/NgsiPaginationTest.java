package it.eng.idra.dcat.dump;

import com.sun.net.httpserver.HttpServer;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.beans.dcat.DcatDataset;
import it.eng.idra.connectors.NgsiLdCbDcatConnector;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NgsiPaginationTest {
  @Test void readsMoreThanOneThousandDatasetsAndCachesSharedSeries() throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    AtomicInteger pages=new AtomicInteger(),seriesRequests=new AtomicInteger();
    server.createContext("/ngsi-ld/v1/entities",exchange->{
      String body;
      if(exchange.getRequestURI().getPath().contains("/entities/")) {
        seriesRequests.incrementAndGet();
        body=new JSONObject().put("id","urn:fixture:series").put("type","DatasetSeries")
            .put("resourceUri",new JSONObject().put("type","Property").put("value","https://example.org/series"))
            .put("title",new JSONObject().put("type","Property").put("value","Budgets")).toString();
      } else {
        pages.incrementAndGet();
        int offset=exchange.getRequestURI().getQuery().contains("offset=1000")?1000:0;
        JSONArray array=new JSONArray();
        for(int i=offset;i<(offset==0?1000:1001);i++) array.put(new JSONObject()
            .put("id","urn:fixture:dataset:"+i).put("type","Dataset")
            .put("title",new JSONObject().put("type","Property").put("value","Budget "+i))
            .put("inSeries",new JSONObject().put("type","Relationship").put("object","urn:fixture:series")));
        body=array.toString();
      }
      byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
      exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    });server.start();
    try {
      OdmsCatalogue node=new OdmsCatalogue();node.setId(999999);node.setAutoUpdate(0);
      node.setHost("http://127.0.0.1:"+server.getAddress().getPort());
      List<DcatDataset> datasets=new NgsiLdCbDcatConnector(node).getAllDatasets();
      assertEquals(1001,datasets.size());assertEquals(2,pages.get());assertEquals(1,seriesRequests.get());
      assertEquals("https://example.org/series",datasets.get(1000).getInSeries().get(0).getResourceUri());
    } finally {server.stop(0);}
  }
}
