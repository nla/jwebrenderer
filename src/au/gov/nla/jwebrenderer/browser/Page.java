package au.gov.nla.jwebrenderer.browser;

import com.grack.nanojson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class Page implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Page.class);
    private final ChromeRpc rpc;
    private final String targetId;
    private final String sessionId;
    private final Map<String,Consumer<JsonObject>> handlers = new ConcurrentHashMap<>();

    public Page(ChromeRpc rpc, String targetId) {
        this.rpc = rpc;
        this.targetId = targetId;
        this.sessionId = rpc.call("Target.attachToTarget", Map.of("targetId", targetId, "flatten", true)).getString("sessionId");
        rpc.registerSession(sessionId, this::handle);
        call("Page.enable", Map.of());
    }

    public JsonObject call(String method, Map<String, Object> params) {
        return rpc.call(sessionId, method, params);
    }

    private void handle(JsonObject message) {
        Consumer<JsonObject> handler = handlers.get(message.getString("method"));
        log.trace("Handling {}", message);
        if (handler != null) {
            handler.accept(message.getObject("params"));
        }
    }

    @Override
    public void close() {
        rpc.call("Target.closeTarget", Map.of("targetId", targetId));
        rpc.deregisterSession(sessionId);
    }

    public void navigate(String url) throws NavigationException {
        JsonObject response = call("Page.navigate", Map.of("url", url));
        if (response.has("errorText")) {
            throw new NavigationException(url, response.getString("errorText"));
        }
    }

    public void load(String url, long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
        CompletableFuture<JsonObject> future = futureEvent("Page.loadEventFired");
        navigate(url);
        try {
            future.get(timeout, timeUnit);
        } catch (ExecutionException e) {
            throw ChromeRpc.unwrap(e);
        }
    }

    CompletableFuture<JsonObject> futureEvent(String event) {
        var future = new CompletableFuture<JsonObject>();
        handlers.put(event, future::complete);
        return future.whenComplete((t, e) -> handlers.remove(event));
    }

    public byte[] screenshot(Map<String, Object> clip) {
        return Base64.getDecoder().decode(call("Page.captureScreenshot",
                clip == null ? Map.of() : Map.of("clip", clip)).getString("data"));
    }

    public void hideScrollbars() {
        call("Runtime.evaluate", Map.of("expression", "document.getElementsByTagName('body')[0].style.overflow='hidden'"));
    }
}
