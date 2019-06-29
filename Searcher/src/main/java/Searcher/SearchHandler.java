package Searcher;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.morfologik.MorfologikAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.jline.terminal.Terminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

class SearchHandler {


    private static Logger logger = LoggerFactory.getLogger(SearchHandler.class);
    private int limit;
    private Boolean details;
    private Boolean lang_pol;
    private Boolean color;
    private String queryType;
    private IndexSearcher searcher;

    SearchHandler(Path indexPath) throws IOException {
        limit = Integer.MAX_VALUE;
        details = false;
        lang_pol = false;
        color = false;
        queryType = "term";
        logger.info("Trying to open index {} for searching", indexPath);
        searcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(indexPath)));
        logger.info("Successfully created searchHandler for {}", indexPath);
    }

    void setLang(String langName) throws Exception {
        if (langName.equals("pl")) {
            this.lang_pol = true;
        } else if (langName.equals("en")) {
            this.lang_pol = false;
        } else {
            logger.info("Incorrect setLang input");
            throw new Exception("Incorrect input");
        }

    }

    void setDetails(String setting) throws Exception {
        if (setting.equals("on")) {
            this.details = true;
            logger.info("Setting details to true");
        } else if (setting.equals("off")) {
            this.details = false;
            logger.info("Setting details to false");
        } else {
            logger.info("Incorrect setDetails input");
            throw new Exception("Incorrect input");
        }
    }

    void setLimit(String limit) throws Exception {
        try {
            this.limit = Integer.parseInt(limit);
            if (this.limit == 0) {
                this.limit = Integer.MAX_VALUE;
            }
            logger.info("Setting limit to {}", limit);
        } catch (NumberFormatException e) {
            logger.info("Incorrect setLimit input");
            throw new Exception("Incorrect input");
        }
    }

    void setColor(String setting) throws Exception {
        if (setting.equals("on")) {
            this.color = true;
            logger.info("Setting color to true");
        } else if (setting.equals("off")) {
            this.color = false;
            logger.info("Setting color to false");
        } else {
            logger.info("Incorrect setColor input");
            throw new Exception("Incorrect input");
        }
    }

    void setQueryTerm() {
        logger.info("Setting query to term");
        this.queryType = "term";
    }

    void setQueryPhrase() {
        logger.info("Setting query to phrase");
        this.queryType = "phrase";
    }

    void setQueryFuzzy() {
        logger.info("Setting query to fuzzy");
        this.queryType = "fuzzy";
    }

    void doQuery(String line, Terminal terminal) throws Exception {
        Query query;
        Analyzer analyzer;
        String[] allFields;
        if (lang_pol) {
            analyzer = new MorfologikAnalyzer();
            allFields = new String[]{"body-pl", "name-pl"};
        } else {
            analyzer = new EnglishAnalyzer();
            allFields = new String[]{"body-en", "name-en"};
        }
        switch (queryType) {
            case "term": {

                String term1 = getTerm(line, analyzer);
                if (lang_pol) {
                    query = new BooleanQuery.Builder()
                            .add(new TermQuery(new Term("body-pl", term1)), BooleanClause.Occur.SHOULD)
                            .add(new TermQuery(new Term("name-pl", term1)), BooleanClause.Occur.SHOULD)
                            .build();
                } else {
                    query = new BooleanQuery.Builder()
                            .add(new TermQuery(new Term("body-en", term1)), BooleanClause.Occur.SHOULD)
                            .add(new TermQuery(new Term("name-en", term1)), BooleanClause.Occur.SHOULD)
                            .build();
                }
                break;
            }
            case "phrase":
                if (lang_pol) {
                    QueryBuilder tempBuilder = new QueryBuilder(analyzer);
                    query = new BooleanQuery.Builder()
                            .add(tempBuilder.createPhraseQuery("body-pl", line), BooleanClause.Occur.SHOULD)
                            .add(tempBuilder.createPhraseQuery("name-pl", line), BooleanClause.Occur.SHOULD)
                            .build();
                } else {
                    QueryBuilder tempBuilder = new QueryBuilder(analyzer);
                    query = new BooleanQuery.Builder()
                            .add(tempBuilder.createPhraseQuery("body-en", line), BooleanClause.Occur.SHOULD)
                            .add(tempBuilder.createPhraseQuery("name-en", line), BooleanClause.Occur.SHOULD)
                            .build();
                }
                break;
            case "fuzzy": {
                String term1 = getTerm(line, analyzer);
                if (lang_pol) {
                    query = new BooleanQuery.Builder()
                            .add(new FuzzyQuery(new Term("body-pl", term1)), BooleanClause.Occur.SHOULD)
                            .add(new FuzzyQuery(new Term("name-pl", term1)), BooleanClause.Occur.SHOULD)
                            .build();
                } else {
                    query = new BooleanQuery.Builder()
                            .add(new FuzzyQuery(new Term("body-en", term1)), BooleanClause.Occur.SHOULD)
                            .add(new FuzzyQuery(new Term("name-en", term1)), BooleanClause.Occur.SHOULD)
                            .build();
                }
                break;
            }
            default:
                throw new Exception();
        }
        TopDocs results = searcher.search(query, limit);
        if (!details) {
            terminal.writer().println("File count: " + results.totalHits.value);
            ScoreDoc[] hits = results.scoreDocs;
            for (ScoreDoc hit : hits) {
                terminal.writer().println();
                terminal.writer().println(searcher.doc(hit.doc).get("fullPath"));
            }
        } else {
            try {
                Formatter formatter;
                if (color) {
                    formatter = new SimpleHTMLFormatter("\u001b[31m", "\u001b[0m");
                } else {
                    formatter = new SimpleHTMLFormatter();
                }

                terminal.writer().println("File count: " + results.totalHits.value);
                ScoreDoc[] hits = results.scoreDocs;
                Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
                for (ScoreDoc hit : hits) {
                    terminal.writer().println();
                    terminal.writer().println("\u001B[1m" + searcher.doc(hit.doc).get("fullPath") + "\u001b[0m");
                    String[] frags = highlighter.getBestFragments(analyzer, allFields[1], searcher.doc(hit.doc).get(allFields[0]), 10);
                    for (String frag : frags) {
                        terminal.writer().println(frag);
                    }
                }
            } catch (Exception e) {
                logger.error("Error");
                throw e;
            }
        }
    }

    private String getTerm(String line, Analyzer analyzer) throws Exception {
        TokenStream stream = analyzer.tokenStream("", line);
        CharTermAttribute charTermAttribute = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        if (!stream.incrementToken()) {
            logger.warn("No term produced");
            throw new Exception();
        }
        String term1 = charTermAttribute.toString();
        logger.info("Term reduced to {}", term1);
        stream.close();
        return term1;
    }
}
