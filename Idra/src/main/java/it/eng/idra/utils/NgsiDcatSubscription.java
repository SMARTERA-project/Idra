package it.eng.idra.utils;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.json.*;

/** Upgrades only the subscription owned by this Idra catalogue. */
public final class NgsiDcatSubscription {
  private NgsiDcatSubscription() { }
  public static void ensure(String host, int nodeId, String endpoint) throws Exception {
    String url=host+"/ngsi-ld/v1/subscriptions/urn:ngsi-ld:Subscription:"+nodeId;
    Map<String,String> headers=new HashMap<>();NgsiDcatReader.contextHeaders(headers);
    HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    HttpRequest.Builder get=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();
    headers.forEach(get::header);
    HttpResponse<String> existing=client.send(get.build(),HttpResponse.BodyHandlers.ofString());
    if(existing.statusCode()!=200 && existing.statusCode()!=404)
      throw new IllegalStateException("Cannot inspect NGSI subscription: HTTP "+existing.statusCode());
    boolean update=existing.statusCode()==200;
    JSONObject notification=update?new JSONObject(existing.body()).optJSONObject("notification"):null;
    if(notification==null)notification=new JSONObject();
    notification.remove("attributes");
    notification.put("format","normalized");
    // Retain delivery options of an existing subscription, including its endpoint.
    if(!notification.has("endpoint"))notification.put("endpoint",new JSONObject().put("uri",endpoint).put("accept","application/json"));
    JSONArray entities=new JSONArray();
    for(String type:Arrays.asList("http://www.w3.org/ns/dcat#Dataset","http://www.w3.org/ns/dcat#Distribution","http://www.w3.org/ns/dcat#DatasetSeries","http://www.w3.org/ns/dcat#DataService",
        "https://uri.etsi.org/ngsi-ld/default-context/Dataset","https://uri.etsi.org/ngsi-ld/default-context/DistributionDCAT-AP","https://smartdatamodels.org/dataModel.DCAT-AP/Dataset","https://smartdatamodels.org/dataModel.DCAT-AP/DistributionDCAT-AP"))
      entities.put(new JSONObject().put("type",type));
    JSONObject body=new JSONObject().put("entities",entities).put("notification",notification);
    if(!update)body.put("id","urn:ngsi-ld:Subscription:"+nodeId).put("type","Subscription");
    HttpRequest.Builder request=HttpRequest.newBuilder(URI.create(update?url:host+"/ngsi-ld/v1/subscriptions/"))
        .timeout(Duration.ofSeconds(30)).header("Content-Type","application/json")
        .method(update?"PATCH":"POST",HttpRequest.BodyPublishers.ofString(body.toString()));
    headers.forEach(request::header);
    HttpResponse<String> response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()<200 || response.statusCode()>=300 || response.statusCode()==207)
      throw new IllegalStateException("Cannot save NGSI subscription: HTTP "+response.statusCode());
  }
}
