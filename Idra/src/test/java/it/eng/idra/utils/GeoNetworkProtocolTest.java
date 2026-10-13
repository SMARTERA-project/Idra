package it.eng.idra.utils;

import it.eng.idra.beans.odms.OdmsCatalogue;
import it.eng.idra.connectors.GeoNetworkConnector;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoNetworkProtocolTest {
    private HttpURLConnection connection(String url) throws Exception {
        return new HttpURLConnection(new URL(url)) {
            public void connect() { }
            public void disconnect() { }
            public boolean usingProxy() { return false; }
        };
    }

    @Test void prefersHttpsWhenBothWorkAndKeepsQueryForLaterPages() throws Exception {
        GeoNetworkConnectorUtils.EndpointConnection endpoint = new GeoNetworkConnectorUtils.EndpointConnection();
        List<String> requests = new ArrayList<>();
        GeoNetworkConnectorUtils.EndpointConnection.ConnectionOpener opener = url -> {
            requests.add(url);
            return connection(url);
        };
        endpoint.open("http://catalogue.example/csw?token=a&startPosition=1", opener);
        endpoint.open("http://catalogue.example/csw?token=a&startPosition=101", opener);
        assertEquals(List.of("https://catalogue.example/csw?token=a&startPosition=1",
                "https://catalogue.example/csw?token=a&startPosition=101"), requests);
    }

    @Test void usesHttpsWithoutContactingUnavailableHttp() throws Exception {
        HttpURLConnection conn = new GeoNetworkConnectorUtils.EndpointConnection()
                .open("http://catalogue.example/csw", url -> {
                    assertTrue(url.startsWith("https://"));
                    return connection(url);
                });
        assertEquals("https", conn.getURL().getProtocol());
    }

    @Test void fallsBackToHttpAndDoesNotRetryHttpsForLaterRequests() throws Exception {
        GeoNetworkConnectorUtils.EndpointConnection endpoint = new GeoNetworkConnectorUtils.EndpointConnection();
        List<String> requests = new ArrayList<>();
        GeoNetworkConnectorUtils.EndpointConnection.ConnectionOpener opener = url -> {
            requests.add(url);
            if (url.startsWith("https:")) throw new java.net.ConnectException("Connection refused");
            return connection(url);
        };
        endpoint.open("https://catalogue.example/csw?page=1", opener);
        endpoint.open("https://catalogue.example/csw?page=2", opener);
        assertEquals(List.of("https://catalogue.example/csw?page=1", "http://catalogue.example/csw?page=1",
                "http://catalogue.example/csw?page=2"), requests);
    }

    @Test void preservesBothErrorsAndRetriesSelectionAfterBothFail() throws Exception {
        GeoNetworkConnectorUtils.EndpointConnection endpoint = new GeoNetworkConnectorUtils.EndpointConnection();
        Exception error = assertThrows(Exception.class, () -> endpoint.open("http://catalogue.example/csw",
                url -> { throw new Exception(url.startsWith("https:") ? "Connection refused" : "HTTP 404"); }));
        assertEquals("HTTP 404", error.getMessage());
        assertEquals("Connection refused", error.getSuppressed()[0].getMessage());
        assertEquals("https", endpoint.open("http://catalogue.example/csw", this::connection)
                .getURL().getProtocol());
    }

    @Test void convertsStandardPortsAndPreservesCustomPortsAndQuery() throws Exception {
        for (String url : List.of("http://catalogue.example:80/csw?schema=http://example.org",
                "https://catalogue.example:443/csw?schema=http://example.org")) {
            assertEquals("https://catalogue.example/csw?schema=http://example.org",
                    new GeoNetworkConnectorUtils.EndpointConnection().open(url, this::connection).getURL().toString());
        }
        assertEquals("https://catalogue.example:8080/csw?schema=http://example.org",
                new GeoNetworkConnectorUtils.EndpointConnection()
                        .open("http://catalogue.example:8080/csw?schema=http://example.org", this::connection)
                        .getURL().toString());
    }

    @Test void fallsBackWhenHttpsReturnsHtmlOrAnHttpError() throws Exception {
        for (String error : List.of("Unexpected HTML response (possible portal/login)", "HTTP 404", "HTTP 403")) {
            HttpURLConnection conn = new GeoNetworkConnectorUtils.EndpointConnection()
                    .open("https://catalogue.example/csw", url -> {
                        if (url.startsWith("https:")) throw new Exception(error);
                        return connection(url);
                    });
            assertEquals("http", conn.getURL().getProtocol());
        }
    }

    @Test void remembersProtocolReachedThroughRedirects() throws Exception {
        GeoNetworkConnectorUtils.EndpointConnection endpoint = new GeoNetworkConnectorUtils.EndpointConnection();
        endpoint.open("https://catalogue.example/csw", url -> connection("http://catalogue.example/csw"));
        assertEquals("http", endpoint.open("https://catalogue.example/csw?page=2", this::connection)
                .getURL().getProtocol());
    }

    @Test void connectorUsesHttpFallbackForCountsPaginationAndSearch() throws Exception {
        AtomicInteger tlsAttempts = new AtomicInteger(), httpRequests = new AtomicInteger();
        // Reject TLS immediately, serve valid CSW XML over HTTP on the same custom port.
        try (ServerSocket server = new ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(3000);
                        int first = socket.getInputStream().read();
                        if (first != 'G') { tlsAttempts.incrementAndGet(); continue; }
                        BufferedReader reader = new BufferedReader(new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII));
                        String request = "G" + reader.readLine();
                        while (!reader.readLine().isEmpty()) { }
                        httpRequests.incrementAndGet();
                        int next = request.contains("resultType=results") && request.contains("startPosition=1&") ? 2 : 0;
                        String xml = "<csw:GetRecordsResponse xmlns:csw=\"http://www.opengis.net/cat/csw/2.0.2\">"
                                + "<csw:SearchResults numberOfRecordsMatched=\"2\" numberOfRecordsReturned=\"0\" nextRecord=\""
                                + next + "\"/></csw:GetRecordsResponse>";
                        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\n"
                                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().write(body);
                    } catch (java.io.IOException e) {
                        if (!server.isClosed()) throw new RuntimeException(e);
                    }
                }
            });
            responder.setDaemon(true);
            responder.start();
            OdmsCatalogue node = new OdmsCatalogue();
            node.setHost("http://127.0.0.1:" + server.getLocalPort() + "/csw");
            GeoNetworkConnector connector = new GeoNetworkConnector(node);
            assertEquals(2, connector.countDatasets());
            assertTrue(connector.getAllDatasets().isEmpty());
            assertEquals(2, connector.countSearchDatasets(new HashMap<>()));
            HashMap<String, Object> search = new HashMap<>();
            search.put("page", 1);
            search.put("size", 100);
            assertTrue(connector.findDatasets(search).isEmpty());
            assertEquals(1, tlsAttempts.get());
            assertEquals(5, httpRequests.get());
        }
    }
}
