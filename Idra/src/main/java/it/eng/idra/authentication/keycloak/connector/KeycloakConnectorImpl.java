/*******************************************************************************
 * Idra - Open Data Federation Platform
 * Copyright (C) 2021 Engineering Ingegneria Informatica S.p.A.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/

package it.eng.idra.authentication.keycloak.connector;

import com.google.gson.Gson;
import it.eng.idra.authentication.fiware.model.GrantErrorMessage;
import it.eng.idra.authentication.fiware.model.Token;
import it.eng.idra.authentication.keycloak.model.KeycloakUser;
import it.eng.idra.utils.restclient.RestClient;
import it.eng.idra.utils.restclient.RestClientImpl;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpResponse;

// TODO: Auto-generated Javadoc
/**
 * Integration with Keycloak.
 * 
 * 
 */
public class KeycloakConnectorImpl extends KeycloakConnector {

  /**
   * Instantiates a new keycloak connector impl.
   *
   * @param protocol     the protocol
   * @param host         the host
   * @param port         the port
   * @param clientId     the client id
   * @param clientSecret the client secret
   * @param redirectUri  the redirect uri
   */
  public KeycloakConnectorImpl(String protocol, String host, int port, String clientId,
      String clientSecret, String redirectUri) {
    super(protocol, host, port, clientId, clientSecret, redirectUri);
  }

  /**
   * Get token.
   *
   * @param code the code
   * @return the token
   * @throws Exception the exception
   */
  public Token getToken(String code) throws Exception {

    Optional<Token> token = Optional.empty();

    String url = baseUrl + path_token;
    String auth = "Basic "
        + new String(Base64.getEncoder().encode((clientId + ":" + clientSecret).getBytes()));

    Map<String, String> headers = new HashMap<String, String>();
    headers.put("Authorization", auth);

    String reqData = "grant_type=authorization_code" + "&code=" + code + "&redirect_uri="
        + redirectUri + "&scope=openid";

    RestClient client = new RestClientImpl();
    HttpResponse response = client.sendPostRequest(url, reqData,
        MediaType.APPLICATION_FORM_URLENCODED_TYPE, headers);

    String returnedjson = client.getHttpResponseBody(response);
    switch (client.getStatus(response)) {
      case 500:
        GrantErrorMessage errorBody = new Gson().fromJson(returnedjson, GrantErrorMessage.class);
        throw new RuntimeException("[" + errorBody.getStatus() + "] " + errorBody.getMessage());

      default:
        token = Optional.ofNullable(new Gson().fromJson(returnedjson, Token.class));
    }

    return token.get();

  }

  /**
   * BFF token proxy: forwards an OAuth2 token request (authorization_code or
   * refresh_token) to Keycloak, adding the confidential client credentials
   * (client_id:client_secret via HTTP Basic) server-side. The SPA therefore never
   * holds the client secret. Returns the raw Keycloak token-endpoint response so
   * the caller can relay status and body verbatim.
   *
   * @param formData the x-www-form-urlencoded request body forwarded from the SPA
   * @return a 2-element array: [0] = HTTP status code, [1] = response body JSON
   * @throws Exception the exception
   */
  public String[] exchangeToken(String formData) throws Exception {
    String url = baseUrl + path_token;
    String auth = "Basic "
        + new String(Base64.getEncoder().encode((clientId + ":" + clientSecret).getBytes()));

    Map<String, String> headers = new HashMap<String, String>();
    headers.put("Authorization", auth);

    RestClient client = new RestClientImpl();
    HttpResponse response = client.sendPostRequest(url, formData,
        MediaType.APPLICATION_FORM_URLENCODED_TYPE, headers);

    String body = client.getHttpResponseBody(response);
    return new String[] { String.valueOf(client.getStatus(response)), body };
  }

  /**
   * Get user info.
   *
   * @param token the token
   * @return the user
   * @throws Exception the exception
   */
  public KeycloakUser getUserInfo(String token) throws Exception {

    Optional<KeycloakUser> userinfo = Optional.empty();
    // String url = baseUrl + path_user + "?access_token=" + token;
    String url = baseUrl + path_user;

    RestClient client = new RestClientImpl();
    HashMap<String, String> header = new HashMap<String, String>();
    header.put("Authorization", "Bearer " + token);
    HttpResponse response = client.sendGetRequest(url, header);

    String returnedJson = client.getHttpResponseBody(response);
    switch (client.getStatus(response)) {

      case 500:
        GrantErrorMessage errorBody = new Gson().fromJson(returnedJson, GrantErrorMessage.class);
        throw new RuntimeException("[" + errorBody.getStatus() + "] " + errorBody.getMessage());

      default:
        userinfo = Optional.ofNullable(new Gson().fromJson(returnedJson, KeycloakUser.class));
    }

    return userinfo.get();
  }

}
