package au.gov.nla.jwebrenderer.browser;

import java.util.HashMap;
import java.util.Map;

public class BrowserContext implements AutoCloseable {
    private final ChromeRpc rpc;
    private final String id;

    public BrowserContext(ChromeRpc rpc) {
        this.rpc = rpc;
        this.id = rpc.call("Target.createBrowserContext", Map.of("disposeOnDetach", true)).getString("browserContextId");
    }

    public Page createPage(Map<String,Object> options) {
        Map<String,Object> params = new HashMap<>();
        params.put("url", "about:blank");
        params.put("browserContextId", id);
        if (options != null) {
            params.putAll(options);
        }
        String targetId = rpc.call("Target.createTarget", params).getString("targetId");
        return new Page(rpc, targetId);
    }

    @Override
    public void close() {
        rpc.call("Target.disposeBrowserContext", Map.of("browserContextId", id));
    }
}
