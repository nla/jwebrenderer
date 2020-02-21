package au.gov.nla.jwebrenderer.browser;

public class NavigationException extends RuntimeException {
    private final String url;
    private final String errorText;

    public NavigationException(String url, String errorText) {
        super(errorText + " for " + url);
        this.url = url;
        this.errorText = errorText;
    }

    public String getUrl() {
        return url;
    }

    public String getErrorText() {
        return errorText;
    }
}
