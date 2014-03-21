package hello;

import static spark.Spark.*;

import spark.*;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class API {
    static final Locale locale = null;
    static final Logger logger = Logger.getLogger(API.class.toString());

    class Credentials {
        static final String clientId = "TY1ARBVSEIZHZVAZZRHIEQNDJS0FMWIBN0UBL1SBNJQB5Z1Z";
        static final String clientSecret = "BHMPRXUFOU2K2GI4BO5URAQW350L5LJ5P2A2Q3WQBMRNGOHL";
        static final String version = "20131119";

    }

    static class LatLon {
        final String lat;
        final String lon;

        LatLon(String lat, String lon) {
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return String.format("%s,%s", lat, lon);
        }
    }

    static final Function<String, String> credentials = (String url) -> {
        return String.format(locale, "%s&client_id=%s&clientSecret=%s&v=%s",
                url, Credentials.clientId, Credentials.clientSecret, Credentials.version);
    };

    static final JsonReader readUrl(String url) {
        try {
            logger.info(url);
            return Json.createReader(new URL(url).openStream());
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage());
            return null;
        }
    }

    static final Supplier<LatLon> getLocation(String address) {
        return () -> {
            String url = "http://nominatim.openstreetmap.org/search.php?format=json&q=" + URLEncoder.encode(address);
            JsonObject location = readUrl(url).readArray().getJsonObject(0);
            logger.info(location.toString());

            return new LatLon(location.getString("lat"), location.getString("lon"));
        };
    }

    static final Function<LatLon, String> writeLatLon = (LatLon latLon) -> {
        logger.info(latLon.toString());

        StringWriter stringWriter = new StringWriter();
        JsonGenerator generator = Json.createGenerator(stringWriter);

        generator.writeStartObject()
                .write("lat", latLon.lat)
                .write("lon", latLon.lon)
                .writeEnd();

        generator.close();
        return stringWriter.toString();
    };

    public static void main(String[] args) {
        get(new Route("/") {
            @Override
            public Object handle(Request request, Response response) {
                response.type("application/json");
                final String address = request.queryParams("address");
                logger.log(Level.INFO, "Address: " + address);

                if (address == null || address.isEmpty()) {
                    return "{\"error\": \"Empty address\"}";
                } else {
                    CompletableFuture<LatLon> f = CompletableFuture.supplyAsync(getLocation(address));
                    CompletableFuture<String> result = f.thenApplyAsync(writeLatLon);

                    try {
                        return result.get(500, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return "{\"error\": \"External API timeout\"}";
                    }
                }
            }
        });
    }
}