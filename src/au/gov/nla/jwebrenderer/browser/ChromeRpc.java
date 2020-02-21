package au.gov.nla.jwebrenderer.browser;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ChromeRpc {
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Logger log = LoggerFactory.getLogger(ChromeRpc.class);
    private final Executor executor = ForkJoinPool.commonPool();
    private final WebSocket webSocket;
    private final AtomicLong idGenerator = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonObject>> calls = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonObject>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JsonObject>> sessionHandlers = new ConcurrentHashMap<>();

    public ChromeRpc(URI url) {
        this.webSocket = httpClient.newWebSocketBuilder().buildAsync(url, new WebSocket.Listener() {
            StringBuilder buffer = new StringBuilder();
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buffer.append(data);
                if (last) {
                    String message = buffer.toString();
                    executor.execute(() -> handle(message));
                    buffer.setLength(0);
                }
                webSocket.request(1);
                return null;
            }
        }).join();
    }

    private void handle(String data) {
        try {
            var message = JsonParser.object().from(data);
            if (message.has("method")) {
                if (message.has("sessionId")) {
                    var handler = sessionHandlers.get(message.getString("sessionId"));
                    if (handler == null) {
                        log.debug("Event for unhandled session: {}", message);
                    } else {
                        ForkJoinPool.commonPool().submit(() -> handler.accept(message));
                    }
                } else {
                    var handler = handlers.get(message.getString("method"));
                    if (handler == null) {
                        log.debug("Unhandled event: " + message.getString("method"));
                    } else {
                        ForkJoinPool.commonPool().submit(() -> handler.accept(message.getObject("params")));
                    }
                }
            } else {
                long id = message.getLong("id");
                CompletableFuture<JsonObject> future = calls.remove(id);
                if (future == null) {
                    log.warn("Warning: Chrome sent unexpected RPC id {}", id);
                } else if (message.has("error")) {
                    future.completeExceptionally(new RuntimeException(message.getObject("error").getString("message")));
                } else {
                    future.complete(message.getObject("result"));
                }
            }
        } catch (Exception e) {
            log.error("Uncaught exception, closing websocket.", e);
            webSocket.sendClose(500, e.getMessage());
        }
    }

    public JsonObject call(String method, Map<String, Object> params) {
        return call(null, method, params);
    }

    public JsonObject call(String sessionId, String method, Map<String, Object> params) {
        try {
            return callAsync(sessionId, method, params).get(60, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    static RuntimeException unwrap(ExecutionException e) {
        if (e.getCause() instanceof RuntimeException) {
            return (RuntimeException)e.getCause();
        } else {
            return new RuntimeException(e.getCause());
        }
    }

    private CompletableFuture<JsonObject> callAsync(String sessionId, String method, Map<String, Object> params) {
        long id = idGenerator.incrementAndGet();
        String data = JsonWriter.string().object()
                .value("id", id)
                .value("sessionId", sessionId)
                .value("method", method)
                .object("params", params)
                .end()
                .done();
        var future = new CompletableFuture<JsonObject>();
        calls.put(id, future);
        log.debug("send {}", data.substring(0, Math.min(data.length(), 500)));
        webSocket.sendText(data, true);
        return future;
    }

    void registerSession(String sessionId, Consumer<JsonObject> handler) {
        sessionHandlers.put(sessionId, handler);
    }

    void deregisterSession(String sessionId) {
        sessionHandlers.remove(sessionId);
    }

    public void close() {
        webSocket.sendClose(200, "Close").join();
    }

    public boolean isClosed() {
        return webSocket.isInputClosed() || webSocket.isOutputClosed();
    }
}
