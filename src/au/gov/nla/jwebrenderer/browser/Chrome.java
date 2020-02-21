package au.gov.nla.jwebrenderer.browser;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import java.net.MalformedURLException;
import java.net.URI;

public class Chrome implements AutoCloseable {
    private final ChromeRpc rpc;

    public Chrome(URI devtoolsBaseUrl) {
        this.rpc = new ChromeRpc(getWebsocketDebuggerUrl(devtoolsBaseUrl));
    }

    public BrowserContext createContext() {
        return new BrowserContext(rpc);
    }

    @Override
    public void close() {
        rpc.close();
    }

    private static URI getWebsocketDebuggerUrl(URI devtoolsBaseUrl) {
        if (devtoolsBaseUrl.getScheme().equals("ws")) return devtoolsBaseUrl;
        try {
            return URI.create(JsonParser.object().from(devtoolsBaseUrl.resolve("/json/version").toURL())
                    .getString("webSocketDebuggerUrl"));
        } catch (JsonParserException | MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
