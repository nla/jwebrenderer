package au.gov.nla.jwebrenderer;

import au.gov.nla.jwebrenderer.browser.BrowserContext;
import au.gov.nla.jwebrenderer.browser.Chrome;
import au.gov.nla.jwebrenderer.browser.Page;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Renderer {
    private final Chrome chrome;

    public Renderer(String chromeHost, int chromePort) {
        chrome = new Chrome(URI.create("http://" + chromeHost + ":" + chromePort));
    }

    public byte[] render(String url, int viewportWidth, int viewportHeight, Map<String, Object> clip, int timeout, int sleep) throws TimeoutException, InterruptedException {
        try (BrowserContext context = chrome.createContext();
             Page page = context.createPage(Map.of("width", viewportWidth, "height", viewportHeight))) {
                page.load(url, timeout, TimeUnit.MILLISECONDS);
                page.hideScrollbars();
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                return page.screenshot(clip);
        }
    }
}
