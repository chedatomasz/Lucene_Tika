package Indexer;

import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.langdetect.OptimaizeLangDetector;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

import static java.lang.Math.toIntExact;

public class IndexHandler implements AutoCloseable {
    private static Logger logger = LoggerFactory.getLogger(IndexHandler.class);
    private Directory dir;

    IndexHandler(String operatingDirectory) throws IOException {
        Path path = Paths.get(operatingDirectory);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            logger.error("Directory does not exist and cannot be created: {}", path);
            throw e;
        }
        try {
            dir = FSDirectory.open(path);
        } catch (IOException e) {
            logger.error("FSDirectory could not open path: {}", path);
            throw e;
        }
    }

    private String sanitizeDirectoryPath(String path) throws IOException {
        Path unsanitized = Paths.get(path);
        if (!Files.exists(unsanitized) || !Files.isDirectory(unsanitized)) {
            logger.error("Path sanitization failed: file not found");
            throw new FileNotFoundException();
        }
        File file = new File(path);
        return file.getCanonicalPath();
    }

    String sanitizeAnyPath(String path) throws IOException {
        Path unsanitized = Paths.get(path);
        if (!Files.exists(unsanitized)) {
            logger.error("Path sanitization failed: file not found");
            throw new FileNotFoundException();
        }
        File file = new File(path);
        return file.getCanonicalPath();
    }


    private void addSingleFileToIndex(Path path, LanguageDetector detector, Tika tika) throws IOException {
        try {
            String body = tika.parseToString(path);
            String name = path.getFileName().toString();
            String fullPath = sanitizeAnyPath(path.toString());
            LanguageResult identification = detector.detect(body);
            if (!identification.isReasonablyCertain()) {
                logger.warn("Not reasonably certain language (but probably {}): {}", identification.getLanguage(), fullPath);
                throw new TikaException("not reasonably certain: maybe unknown language");
            }
            if (identification.isLanguage("pl")) {
                logger.info("Adding file {} to index. Language is Polish.", fullPath);
                try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new MorfologikAnalyzer())
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
                    Document doc = new Document();
                    Field pathField = new StringField("fullPath", fullPath, Field.Store.YES);
                    doc.add(pathField);
                    Field bodyField = new TextField("body-pl", body, Field.Store.YES);
                    doc.add(bodyField);
                    Field nameField = new TextField("name-pl", name, Field.Store.YES);
                    doc.add(nameField);
                    writer.updateDocument(new Term("fullPath", fullPath), doc);
                }
            } else if (identification.isLanguage("en")) {
                logger.info("Adding file {} to index. Language is English.", fullPath);
                try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new EnglishAnalyzer())
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
                    Document doc = new Document();
                    Field pathField = new StringField("fullPath", fullPath, Field.Store.YES);
                    doc.add(pathField);
                    Field bodyField = new TextField("body-en", body, Field.Store.YES);
                    doc.add(bodyField);
                    Field nameField = new TextField("name-en", name, Field.Store.YES);
                    doc.add(nameField);
                    writer.updateDocument(new Term("fullPath", fullPath), doc);
                }
            } else {
                logger.warn("Language not Polish or English - detected as {}: {}", identification.getLanguage(), fullPath);
                throw new TikaException("unsupported language");
            }
        } catch (TikaException e) {
            logger.warn("Tika threw an exception at: {}", path);
            throw new IOException();
        } catch (IOException e) {
            logger.warn("Something other that Tika extractor threw an exception at: {}", path);
            throw new IOException();
        }
    }


    void addAllToIndex(String path, Boolean storePath) throws IOException {

        if (storePath) {
            Path sanitizedPath = Paths.get(sanitizeDirectoryPath(path));
            Document doc = new Document();
            logger.info("Adding path {} which is derived from {}", sanitizedPath.toString(), path);
            Field pathField = new StringField("StoredPath", sanitizedPath.toString(), Field.Store.YES);
            doc.add(pathField);
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
                writer.addDocument(doc);
            } catch (IOException e) {
                logger.error("IOException in addAllToIndex: cannot access index");
                throw e;
            }
        }
        LanguageDetector detector = new OptimaizeLangDetector().loadModels();
        Tika tika = new Tika();
        Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    addSingleFileToIndex(file, detector, tika);
                } catch (IOException ignore) {
                    logger.info("Did not index file {} - exception occured", file);
                    // don't index files that can't be read.
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void removeSingleFileFromIndex(Path file, IndexWriter writer) throws IOException {
        logger.info("Removing single file {}", file);
        writer.deleteDocuments(new Term("fullPath", sanitizeAnyPath(file.toString())));
    }

    void removeAllFromIndex(String path) throws IOException {
        Path sanitizedPath = Paths.get(sanitizeDirectoryPath(path));
        if (!getAllRegistered().contains(sanitizedPath.toString())) {
            logger.error("Path has not been previously added: {}", path);
            throw new IOException();
        }
        logger.info("Removing path {} which is derived from {}", sanitizedPath.toString(), path);
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
            writer.deleteDocuments(new Term("StoredPath", sanitizedPath.toString()));
        } catch (IOException e) {
            logger.error("IOException in removeAllFromIndex: cannot access index");
            throw e;
        }
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
            Files.walkFileTree(sanitizedPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        removeSingleFileFromIndex(file, writer);
                    } catch (IOException ignore) {
                        logger.info("Removing file {} from index unsuccessful", file);
                        // don't stop deindexing if files can't be found or modified.
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("IOException in removeAllFromIndex: cannot access index (2)");
            throw e;
        }

    }

    void removeAllWithPath(String path) throws IOException {
        logger.info("Removing all with path {}", path);
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND))) {
            writer.deleteDocuments(new WildcardQuery(new Term("fullPath", path + "*")));
        }
    }

    Collection<String> getAllRegistered() throws IOException {
        Collection<String> result = new HashSet<>();
        try (IndexReader reader = DirectoryReader.open(dir)) {
            Query query = new WildcardQuery(new Term("StoredPath", "*"));
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs firstResults = searcher.search(query, 10);
            if (firstResults.totalHits.value == 0) {
                return result;
            }
            ScoreDoc[] finalResults = searcher.search(query, toIntExact(firstResults.totalHits.value)).scoreDocs;
            logger.info("Getting all registered. There are {} results.", toIntExact(firstResults.totalHits.value));
            for (ScoreDoc doc : finalResults) {
                Document realDoc = searcher.doc(doc.doc);
                result.add(realDoc.get("StoredPath"));
            }
        } catch (IOException e) {
            logger.error("IOException in getAllRegistered: cannot access index");
            throw e;
        }
        return result;
    }

    void purgeIndex() throws IOException {
        try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            writer.deleteAll();
        } catch (IOException e) {
            logger.error("IOException in removeAllFromIndex: cannot access index");
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        dir.close();
    }
}
