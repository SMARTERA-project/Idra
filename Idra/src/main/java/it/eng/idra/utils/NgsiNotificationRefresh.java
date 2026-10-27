package it.eng.idra.utils;

import it.eng.idra.beans.dcat.*;
import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.cache.MetadataCacheManager;
import it.eng.idra.utils.restclient.*;
import java.util.*;
import org.apache.http.HttpResponse;
import org.json.*;

/** Rebuilds a dataset from a complete entity after a linked-node notification. */
public final class NgsiNotificationRefresh {
  private NgsiNotificationRefresh() { }
  public static void refresh(OdmsCatalogue node,String entityId,String type) throws Exception {
    Map<String,String> headers=new HashMap<>();NgsiDcatReader.contextHeaders(headers);
    headers.put("Accept","application/json");RestClient client=new RestClientImpl();
    for(DcatDataset dataset:MetadataCacheManager.getAllDatasetsByOdmsCatalogue(node.getId())) {
      boolean affected=false;
      if(type.contains("Series") && dataset.getInSeries()!=null)
        affected=dataset.getInSeries().stream().anyMatch(s->entityId.equals(s.getBrokerId()));
      if(dataset.getDistributions()!=null) for(DcatDistribution distribution:dataset.getDistributions()) {
        if(entityId.equals(distribution.getId()) || distribution.getIdentifier()!=null && entityId.equals(distribution.getIdentifier().getValue())) affected=true;
        if(type.contains("Service") && distribution.getAccessService()!=null)
          affected|=distribution.getAccessService().stream().anyMatch(s->entityId.equals(s.getBrokerId()));
      }
      if(!affected)continue;
      String brokerId=dataset.getId();
      if(!(brokerId.startsWith("urn:") || brokerId.startsWith("http"))) brokerId=dataset.getIdentifier().getValue();
      HttpResponse response=client.sendGetRequest(node.getHost()+"/ngsi-ld/v1/entities/"+java.net.URLEncoder.encode(brokerId,java.nio.charset.StandardCharsets.UTF_8),headers);
      int status=client.getStatus(response);String body=client.getHttpResponseBody(response);
      if(status!=200)throw new IllegalStateException("Cannot refresh linked dataset: HTTP "+status);
      DcatDataset refreshed=NgsiLdCbDcatDeserializer.getDatasetFromJson(new JSONArray().put(new JSONObject(body)).toString(),node);
      refreshed.setId(dataset.getId());
      MetadataCacheManager.updateDataset(node.getId(),refreshed);
    }
  }
}

