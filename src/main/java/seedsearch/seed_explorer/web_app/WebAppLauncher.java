package seedsearch.seed_explorer.web_app;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Starts the Javalin server after game initialization.
 * Called from StandaloneLauncher when seedsearch.web is set.
 */
public class WebAppLauncher {

    private static final int DEFAULT_PORT = 7070;
    private static final int PORT_RETRY_COUNT = 10;

    public static void startServer() {
        int basePort = DEFAULT_PORT;
        String portProp = System.getProperty("seedsearch.web.port");
        if (portProp != null) {
            try {
                basePort = Integer.parseInt(portProp);
            } catch (NumberFormatException ignored) {
            }
        }

        int port = basePort;
        for (int attempt = 0; attempt < PORT_RETRY_COUNT; attempt++) {
            Javalin app = Javalin.create(config -> {
                Path webappDir = Paths.get("webapp").toAbsolutePath();
                if (webappDir.toFile().exists()) {
                    config.addStaticFiles(webappDir.toString(), Location.EXTERNAL);
                } else {
                    config.addStaticFiles("webapp", Location.CLASSPATH);
                }
            });
            ApiController.registerRoutes(app);

            try {
                app.start(port);
                System.out.println("Seed Explorer web app running at http://localhost:" + port);
                return;
            } catch (io.javalin.core.util.JavalinBindException e) {
                if (attempt < PORT_RETRY_COUNT - 1) {
                    System.err.println("Port " + port + " in use, trying " + (port + 1) + "...");
                    port++;
                } else {
                    throw e;
                }
            }
        }
    }
}
