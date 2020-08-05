package au.gov.nla.jwebrenderer;

import org.imgscalr.Scalr;
import spark.Spark;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class JWebRenderer {
    public static void main(String args[]) throws ExecutionException, InterruptedException, IOException, TimeoutException {
        String chromeHost = "127.0.0.1";
        int chromePort = 9292;

        if (System.getenv("PORT") != null) {
            Spark.port(Integer.parseInt(System.getenv("PORT")));
        }

        if (System.getenv("CHROME_HOST") != null) {
            chromeHost = System.getenv("CHROME_HOST");
        }

        if (System.getenv("CHROME_PORT") != null) {
            chromePort = Integer.parseInt(System.getenv("CHROME_PORT"));
        }

        Renderer renderer = new Renderer(chromeHost, chromePort);

        Spark.get("/",(request, response) -> "<!doctype html><form action='/image'>" +
                "<input name=url placeholder=url required><br>" +
                "<input name=size placeholder=size value=256><br>" +
                "<input type=submit value='Get Thumbnail'></form>");

        Spark.get("/image", (request, response) -> {
            String url = request.queryParams("url");
            Integer size = request.queryMap("size").integerValue();
            Integer w = request.queryMap("w").integerValue();
            Integer h = request.queryMap("h").integerValue();
            Integer vpw = or(request.queryMap("vpw").integerValue(), 1280);
            Integer vph = or(request.queryMap("vph").integerValue(), 800);
            String format = or(request.queryParams("format"), "jpeg");
            Integer timeout = or(request.queryMap("timeout").integerValue(), 10000);
            Integer sleep = or(request.queryMap("sleep").integerValue(), 100);
            Map<String, Object> clip = parseClip(request.queryMap("clip").value());

            if (url == null) {
                response.status(400);
                response.type("text/plain");
                return "url is mandatory";
            }

            response.type("image/" + format);
            try {
                byte[] data = renderer.render(url, vpw, vph, clip, timeout, sleep);
                if (w != null || h != null || size != null || clip == null) {
                    if (size == null) size = 256;
                    data = resizeImage(size, w, h, format, data);
                }
                return data;
            } catch (TimeoutException e) {
                response.status(500);
                response.type("text/plain");
                return "timed out";
            }
        });
    }

    private static byte[] resizeImage(Integer size, Integer w, Integer h, String format, byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (w == null || h == null) {
            image = Scalr.resize(image, size);
        } else {
            image = Scalr.resize(image, w, h);
        }

        // strip alpha channel for jpeg
        if (image.getColorModel().hasAlpha() && format.equalsIgnoreCase("jpeg")) {
            BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = copy.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();
            image = copy;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }

    private static <T> T or(T a, T b) {
        return a == null ? b : a;
    }

    public static Map<String, Object> parseClip(String s) {
        if (s == null || s.isBlank()) return null;
        String[] fields = s.split(",");
        Map<String,Object> map = new HashMap<>();
        map.put("x", Double.parseDouble(fields[0]));
        map.put("y", Double.parseDouble(fields[1]));
        map.put("width", Double.parseDouble(fields[2]));
        map.put("height", Double.parseDouble(fields[3]));
        map.put("scale", Double.parseDouble(fields[4]));
        return map;
    }

}
