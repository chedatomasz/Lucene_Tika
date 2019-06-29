package Indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;

public class Indexer {
    private static Logger logger = LoggerFactory.getLogger(Indexer.class);
    private static String savingDirectory = Paths.get(System.getProperty("user.home"), ".index").toString();

    private static String usage = "java -jar " + "Indexer-1.0.0-jar-with-dependencies.jar"
            + " [--purge] [--add DOCS_PATH] [--rm DOCS_PATH] [--list] [--reindex] "
            + " No arguments: watch mode. Index is saved in " + Paths.get(System.getProperty("user.home"), ".index") + ".";

    public static void main(String[] args) {

        try (IndexHandler handler = new IndexHandler(savingDirectory)) {
            if (args.length == 0) {
                logger.info("Entering interactive (watch) mode");
                try {
                    new MonitorHandler(handler).monitor();
                } catch (IOException e) {
                    logger.error("FATAL: Error creating or opetating MonitorHandler");
                    System.exit(1);
                }

            } else if (args[0].equals("--purge") && args.length == 1) {
                logger.info("Purging index...");
                handler.purgeIndex();
            } else if (args[0].equals("--add") && args.length == 2) {
                logger.info("Adding path {}...", args[1]);
                handler.addAllToIndex(args[1], true);
            } else if (args[0].equals("--rm") && args.length == 2) {
                logger.info("Removing path {}...", args[1]);
                handler.removeAllFromIndex(args[1]);
            } else if (args[0].equals("--list") && args.length == 1) {
                logger.info("Listing watched directories...");
                Collection<String> listing = handler.getAllRegistered();
                for (String path : listing) {
                    System.out.println(path);
                }
            } else if (args[0].equals("--reindex") && args.length == 1) {
                logger.info("Reindexing watched directiories...");
                Collection<String> listing = handler.getAllRegistered();
                handler.purgeIndex();
                for (String path : listing) {
                    handler.addAllToIndex(path, true);
                }
            } else {
                System.out.println("Usage :" + usage);
            }
        } catch (IOException e) {
            logger.error("FATAL: IndexHandler exception: IOException while manipulating index");
            System.exit(1);
        }
    }


}
