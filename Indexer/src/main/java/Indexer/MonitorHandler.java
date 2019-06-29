package Indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

class MonitorHandler {
    private static Logger logger = LoggerFactory.getLogger(MonitorHandler.class);
    private final WatchService watcher;
    private final IndexHandler handler;
    private final Map<WatchKey, Path> keys;

    MonitorHandler(IndexHandler handler) throws IOException {
        this.handler = handler;
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
        Collection<String> listings = handler.getAllRegistered();
        for (String path : listings) {
            logger.info("Registering tree rooted at {}", path);
            registerAllUnder(path);
        }
    }

    void monitor() {
        for (; ; ) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                return;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == ENTRY_CREATE) {
                    try {
                        Path eventPath = Paths.get(handler.sanitizeAnyPath(keys.get(key).toString()), event.context().toString());
                        registerAllUnder(eventPath.toString());
                        handler.addAllToIndex(eventPath.toString(), false);
                    } catch (IOException e) {
                        logger.error("Error processing ENTRY_CREATE at {}", Paths.get(keys.get(key).toString(), event.context().toString()));
                    }
                }
                if (event.kind() == ENTRY_DELETE) {
                    try {
                        Path eventPath = Paths.get(handler.sanitizeAnyPath(keys.get(key).toString()), event.context().toString());
                        handler.removeAllWithPath(eventPath.toString());
                    } catch (IOException e) {
                        logger.error("Error processing ENTRY_DELETE at {}", Paths.get(keys.get(key).toString(), event.context().toString()));
                    }
                }
                if (event.kind() == ENTRY_MODIFY) {
                    try {
                        Path eventPath = Paths.get(handler.sanitizeAnyPath(keys.get(key).toString()), event.context().toString());
                        handler.addAllToIndex(eventPath.toString(), false);
                    } catch (IOException e) {
                        logger.error("Error processing ENTRY_MODIFY at {}", Paths.get(keys.get(key).toString(), event.context().toString()));
                    }
                }
                logger.info("Processed event {} with context {} and count {} from key {}", event.kind(), event.context(), event.count(), keys.get(key));

                key.reset();
            }
        }
    }

    private void registerAllUnder(String path) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerSingleDir(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerSingleDir(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        Path prev = keys.get(key);
        if (prev == null) {
            logger.info("register: {}", dir);
        } else {
            if (!dir.equals(prev)) {
                logger.info("update: {} -> {}", prev, dir);
            }
        }
        keys.put(key, dir);
    }

}
