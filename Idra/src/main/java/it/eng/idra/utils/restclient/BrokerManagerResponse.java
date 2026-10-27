package it.eng.idra.utils.restclient;

import org.apache.http.HttpResponse;
import org.json.JSONObject;

/** The current bridge returns an HTTP status and may have no response body. */
public final class BrokerManagerResponse {
  private BrokerManagerResponse() { }

  public static int requireDeletionSuccess(RestClient client, HttpResponse response) throws Exception {
    int status = status(client, response);
    if (status < 200 || status >= 300 || status == 207) {
      throw new it.eng.idra.exception.AppException(it.eng.idra.exception.ErrorCode.ERR_UPSTREAM_UNAVAILABLE,
          "Broker catalogue deletion failed: HTTP " + status);
    }
    return status;
  }

  public static int status(RestClient client, HttpResponse response) throws Exception {
    if (response == null) return -1;
    int status = client.getStatus(response);
    String body = client.getHttpResponseBody(response);
    // Older bridge versions wrapped the result in a JSON body under HTTP 200.
    if (status == 200 && body != null && !body.isBlank() && body.trim().startsWith("{")) {
      return new JSONObject(body).optInt("status", status);
    }
    return status;
  }
}
