package hello;

import static spark.Spark.*;

import spark.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.function.Function;
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

    static final Function<String, String> credentials = (String url) -> {
        return String.format(locale, "%s&client_id=%s&clientSecret=%s&v=%s",
                url, Credentials.clientId, Credentials.clientSecret, Credentials.version);
    };

    static final Function<String, String> readUrl = (String url) -> {
        try {
            final URL oracle = new URL(url);
            final BufferedReader in = new BufferedReader(new InputStreamReader(oracle.openStream()));

            final StringBuffer result = new StringBuffer();
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                result.append(inputLine).append("\n");

            in.close();

            return result.toString();
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage());
        }

        return "";
    };

    static final Function<String, String> getLocationData = (String address) -> {
        return readUrl.apply("http://nominatim.openstreetmap.org/search.php?format=json&q="
                + URLEncoder.encode(address));
    };

    public static void main(String[] args) {
        get(new Route("/") {
            @Override
            public Object handle(Request request, Response response) {
                response.type("application/json");
                final String address = request.queryParams("address");
                logger.log(Level.INFO, "Address: " + address);

                if (address == null || address.isEmpty()) {
                    return "{}";
                } else {
                    return getLocationData.apply(address);
                }
            }
        });

    }

}