package Searcher;

import org.jline.builtins.Completers;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Searcher {
    private static Logger logger = LoggerFactory.getLogger(Searcher.class);

    public static void main(String[] args) {
        logger.info("Warming up...");
        Path indexPath = Paths.get(System.getProperty("user.home"), ".index");
        try (Terminal terminal = TerminalBuilder.builder()
                .jna(false)
                .jansi(true)
                .build()) {
            SearchHandler handler = new SearchHandler(indexPath);
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new Completers.FileNameCompleter())
                    .build();
            while (true) {
                String line;
                try {
                    line = lineReader.readLine("> ");
                    String usage = "Usage: java -jar Searcher-1.0.0-jar-with-dependencies.jar [%lang en/pl] [%details on/off] [%color on/off] [%limit num] [%term/phrase/fuzzy] [query]";
                    if (line == null || line.length() == 0) {
                        logger.info("Incorrect line.");
                        terminal.writer().println(usage);
                    } else if (line.charAt(0) == '%') {
                        String[] tokens = line.split(" ");
                        if (tokens[0].equals("%lang") && tokens.length == 2) {
                            handler.setLang(tokens[1]);
                            continue;
                        }
                        if (tokens[0].equals("%details") && tokens.length == 2) {
                            handler.setDetails(tokens[1]);
                            continue;
                        }
                        if (tokens[0].equals("%color") && tokens.length == 2) {
                            handler.setColor(tokens[1]);
                            continue;
                        }
                        if (tokens[0].equals("%limit") && tokens.length == 2) {
                            handler.setLimit(tokens[1]);
                            continue;
                        }
                        if (tokens[0].equals("%term") && tokens.length == 1) {
                            handler.setQueryTerm();
                            continue;
                        }
                        if (tokens[0].equals("%phrase") && tokens.length == 1) {
                            handler.setQueryPhrase();
                            continue;
                        }
                        if (tokens[0].equals("%fuzzy") && tokens.length == 1) {
                            handler.setQueryFuzzy();
                            continue;
                        }
                        logger.info("Incorrect line.");
                        terminal.writer().println(usage);
                    } else {
                        handler.doQuery(line, terminal);
                    }
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                } catch (Exception e) {
                    logger.error("Error executing line.");
                }
            }
        } catch (IOException e) {
            logger.error("An error has occured", e);
        }

    }
}
