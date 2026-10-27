package it.eng.idra.cache;

import com.sun.net.httpserver.HttpServer;
import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.beans.opendatasoft.Dataset;
import it.eng.idra.connectors.OpenDataSoftConnector;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpenDataSoftPaginationTest {
  static class FixtureDataset extends DcatDataset {
    FixtureDataset(String id) {
      setId(id);setNodeId("999998");
      setIdentifier(new DcatProperty("http://purl.org/dc/terms/identifier",id));
    }
  }
  static class FixtureConnector extends OpenDataSoftConnector {
    FixtureConnector(OdmsCatalogue node) { super(node); }
    @Override public DcatDataset datasetToDcat(Object source, OdmsCatalogue node) {
      return new FixtureDataset(((Dataset)source).getDataset().getDatasetId());
    }
  }
  static HttpServer server(boolean alwaysDuplicate, boolean earlyEnd) throws Exception {
    HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/api/v2/catalog/datasets", exchange -> {
      String query=exchange.getRequestURI().getRawQuery();
      boolean second=query.contains("start=100");
      StringBuilder body=new StringBuilder("{\"total_count\":101,\"datasets\":[");
      if(second) {
        if(!earlyEnd)body.append("{\"dataset\":{\"dataset_id\":\"d").append(alwaysDuplicate||!query.contains("sort=dataset_id")?99:100).append("\"}}");
      } else {
        for(int i=0;i<100;i++){if(i>0)body.append(',');body.append("{\"dataset\":{\"dataset_id\":\"d").append(i).append("\"}}");}
      }
      byte[] bytes=body.append("]}").toString().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    });server.start();return server;
  }
  static FixtureConnector connector(HttpServer server) {
    OdmsCatalogue node=new OdmsCatalogue();node.setId(999998);
    node.setHost("http://127.0.0.1:"+server.getAddress().getPort());
    return new FixtureConnector(node);
  }
  @Test void usesStableOrderingBeforeComputingDeletions() throws Exception {
    HttpServer server=server(false,false);
    try {List<DcatDataset> datasets=connector(server).getAllDatasets();assertEquals(101,datasets.size());}
    finally {server.stop(0);}
  }
  @Test void incompleteOrDuplicatePagesAbortInsteadOfReturningDeletionDelta() throws Exception {
    for(boolean earlyEnd:new boolean[]{true,false}){
      HttpServer server=server(!earlyEnd,earlyEnd);
      try {assertThrows(Exception.class,()->connector(server).getAllDatasets());}
      finally {server.stop(0);}
    }
  }
}
