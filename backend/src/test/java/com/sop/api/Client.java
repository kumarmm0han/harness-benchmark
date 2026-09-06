package com.sop.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal HTTP client for integration tests against a RANDOM_PORT server.
 * Sends the demo identity header when set (default: author).
 */
public final class Client {

  public record Response(int status, String body) {}

  private final String base;
  private final HttpClient http;
  private final AtomicReference<String> identity = new AtomicReference<>();

  public Client(String base) {
    this.base = base;
    this.http = HttpClient.newHttpClient();
  }

  public Client as(String id) { this.identity.set(id); return this; }

  public Response get(String pathAndQuery) {
    return request("GET", pathAndQuery, null);
  }

  public Response put(String path, String jsonBody) {
    return request("PUT", path, jsonBody);
  }

  public Response post(String path, String jsonBody) {
    return request("POST", path, jsonBody);
  }

  private Response request(String method, String pathAndQuery, String jsonBody) {
    URI uri = URI.create(base + pathAndQuery);
    var b = HttpRequest.newBuilder(uri).method(method,
        (jsonBody == null) ? HttpRequest.BodyPublishers.noBody()
                           : HttpRequest.BodyPublishers.ofString(jsonBody));
    b.header("Accept", "application/json");
    if (identity.get() != null) b.header("X-Demo-User", identity.get());
    if (jsonBody != null) b.header("Content-Type", "application/json");
    try {
      HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(r.statusCode(), r.body());
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
