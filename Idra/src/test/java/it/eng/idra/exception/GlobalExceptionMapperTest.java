package it.eng.idra.exception;

import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.Response;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionMapperTest {
  private final GlobalExceptionMapper mapper=new GlobalExceptionMapper();

  @Test void jaxRsClientErrorsKeepTheirStatus() {
    // A JSON body sent to the multipart registration endpoint was answered 500 instead of 415.
    Response unsupported=mapper.toResponse(new NotSupportedException());
    assertEquals(415,unsupported.getStatus());
    JSONObject body=new JSONObject(unsupported.getEntity().toString());
    assertEquals("415",body.getString("statusCode"));
    assertEquals("ERR_BAD_REQUEST",body.getString("errorCode"));
    assertEquals(405,mapper.toResponse(new NotAllowedException("GET")).getStatus());
  }

  @Test void unexpectedFailuresStayServerErrors() {
    Response failure=mapper.toResponse(new IllegalStateException("boom"));
    assertEquals(500,failure.getStatus());
    assertEquals("ERR_INTERNAL",new JSONObject(failure.getEntity().toString()).getString("errorCode"));
  }
}
