package hello;

import static spark.Spark.*;

import spark.*;
import spark.utils.IOUtils;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class API {
    static final Logger logger = Logger.getLogger(API.class.toString());

    static class Credentials {
        static final String clientId = "TY1ARBVSEIZHZVAZZRHIEQNDJS0FMWIBN0UBL1SBNJQB5Z1Z";
        static final String clientSecret = "BHMPRXUFOU2K2GI4BO5URAQW350L5LJ5P2A2Q3WQBMRNGOHL";
        static final String version = "20131119";

        static final String apply(String url) {
            return url + paramsToUrl("client_id", Credentials.clientId, "client_secret",
                    Credentials.clientSecret, "v", Credentials.version);
        }
    }

    static final String paramsToUrl(Object... params) {
        final AtomicBoolean isKey = new AtomicBoolean(true);
        return String.valueOf(Arrays.stream(params).map(Object::toString).map((String param) -> {
            return isKey.getAndSet(!isKey.get()) ? param + "=" : param + "&";
        }).collect(Collectors.joining("")));
    }

    ;

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

    static final JsonReader readUrl(String url) {
        try {
            logger.info(url);
            return Json.createReader(new URL(url).openStream());
        } catch (Exception e) {
            logger.warning(e.getMessage());
            return null;
        }
    }

    static final String error(String msg) {
        logger.warning("Error: " + msg);
        return "{\"error\": \"" + msg + "\"}";
    }

    static final <T> T logAndReturn(T t) {
        logger.info(t.toString());
        return t;
    }

    static final Supplier<LatLon> getLocation(String address) {
        return () -> {
            String url = "http://nominatim.openstreetmap.org/search.php?format=json&q=" + URLEncoder.encode(address);
            JsonObject location = logAndReturn(readUrl(url).readArray().getJsonObject(0));

            return logAndReturn(new LatLon(location.getString("lat"), location.getString("lon")));
        };
    }

    static final Function<LatLon, JsonReader> getFoursquareTrends = (LatLon latLon) -> {
        String url = logAndReturn(Credentials.apply("https://api.foursquare.com/v2/venues/trending?") +
                paramsToUrl("limit", 10, "radius", 1000, "ll", (latLon.lat + "," + latLon.lon)));

        try {
            return readUrl(url);
        } catch (Exception e) {
            return null;
        }
    };

    static final Function<JsonReader, String> transformFoursquareTrends = (JsonReader reader) -> {
        JsonObject response = reader.readObject();
        if (response.getJsonObject("meta").getInt("code") != 200) {
            return error("Foursquare API error");
        }

        StringWriter writer = new StringWriter();
        JsonGenerator generator = Json.createGenerator(writer);

        generator.writeStartArray();

        response.getJsonObject("response").getJsonArray("venues").stream().forEach((JsonValue value) -> {
            JsonObject venue = (JsonObject) value;
            generator.writeStartObject().write("name", venue.getString("name"))
                    .write("address", venue.getJsonObject("location").getString("address"))
                    .writeEnd();
        });

        generator.writeEnd();
        generator.close();

        return logAndReturn(writer.toString());
    };

    public static void main(String[] args) {
        get(new Route("/") {
            @Override
            public Object handle(Request request, Response response) {
                response.type("application/json");
                final String address = logAndReturn(request.queryParams("address"));

                if (address == null || address.isEmpty()) {
                    return error("Empty address");
                } else {
                    CompletableFuture<String> result = CompletableFuture.supplyAsync(getLocation(address))
                            .thenApplyAsync(getFoursquareTrends).thenApplyAsync(transformFoursquareTrends);

                    try {
                        return result.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        logger.warning(e.getMessage());
                        return error("External API timeout");
                    }
                }
            }
        });
    }
}