package au.gov.nla.jwebrenderer.browser;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;

public class Chrome implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(Chrome.class);
    private final URI devtoolsBaseUrl;
    private ChromeRpc rpc;

    public Chrome(URI devtoolsBaseUrl) {
        this.devtoolsBaseUrl = devtoolsBaseUrl;
        this.rpc = new ChromeRpc(getWebsocketDebuggerUrl(devtoolsBaseUrl));
    }

    public BrowserContext createContext() {
        if (rpc.isClosed()) {
            log.warn("Connection to Chrome lost attempting to reconnect");
            rpc = new ChromeRpc(getWebsocketDebuggerUrl(devtoolsBaseUrl));
        }
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
