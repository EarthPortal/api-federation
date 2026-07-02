package org.semantics.apigateway.artefacts.search;

import lombok.NoArgsConstructor;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.spans.SpanFirstQuery;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import org.apache.commons.text.similarity.CosineSimilarity;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@NoArgsConstructor
public class SearchLocalIndexerService {

    public static final String INDEXED_FIELD = "label";

    // Common function words that carry little search relevance on their own. Excluded from
    // anchor/individual-term boosting so that e.g. "de" in "larve de poisson" doesn't inflate the
    // score of unrelated labels like "De-anonymisation" or "de-extinction" (tokenized to "de" + ...).
    // Phrase matching (which needs the full expression, connectors included) is left untouched.
    private static final Set<String> STOPWORDS = Set.of(
            // French
            "de", "du", "des", "le", "la", "les", "un", "une", "et", "en", "à", "au", "aux", "ce", "ces", "que", "qui",
            // English
            "the", "of", "and", "in", "on", "at", "a", "an", "to", "for", "is", "are"
    );

    public List<Map<String, Object>> sortByCosineSimilarity(String query, List<Map<String, Object>> results) {
        CosineSimilarity cosineSimilarity = new CosineSimilarity();
        Map<CharSequence, Integer> queryVector = toWordVector(query.toLowerCase());

        return results.stream()
                .sorted((a, b) -> {
                    String labelA = a.get("label") != null ? a.get("label").toString().toLowerCase() : "";
                    String labelB = b.get("label") != null ? b.get("label").toString().toLowerCase() : "";

                    Map<CharSequence, Integer> vectorA = toWordVector(labelA);
                    Map<CharSequence, Integer> vectorB = toWordVector(labelB);

                    double scoreA = cosineSimilarity.cosineSimilarity(queryVector, vectorA);
                    double scoreB = cosineSimilarity.cosineSimilarity(queryVector, vectorB);

                    return Double.compare(scoreB, scoreA);
                })
                .collect(Collectors.toList());
    }

    private Map<CharSequence, Integer> toWordVector(String text) {
        Map<CharSequence, Integer> vector = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return vector;
        }
        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            vector.put(word, vector.getOrDefault(word, 0) + 1);
        }
        return vector;
    }


    public List<Map<String, Object>> reIndexResults(String query, List<Map<String, Object>> combinedResults, Logger logger) throws IOException, ParseException {
        Directory index = indexResults(combinedResults);

        List<Map<String, Object>> localIndexedResult = localIndexSearch(query, logger, index, INDEXED_FIELD);

        List<Map<String, Object>> ranked = localIndexedResult.stream().map(x -> {
                    Map<String, Object> original = combinedResults.stream()
                            .filter(y -> Objects.equals(y.get("iri"), x.get("iri"))
                                    && Objects.equals(y.get("backend_type"), x.get("backend_type"))
                                    && Objects.equals(y.get("ontology"), x.get("ontology")))
                            .findFirst().orElse(null);
                    if (original != null && x.get("score") != null) {
                        original.put("score", x.get("score"));
                    }
                    return original;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> rankedKeys = ranked.stream()
                .map(item -> item.get("iri") + "|" + item.get("backend_type") + "|" + item.get("ontology"))
                .collect(Collectors.toSet());

        List<Map<String, Object>> leftovers = combinedResults.stream()
                .filter(item -> !rankedKeys.contains(item.get("iri") + "|" + item.get("backend_type") + "|" + item.get("ontology")))
                .peek(item -> item.put("score", 0.0f))
                .collect(Collectors.toList());

        List<Map<String, Object>> merged = new ArrayList<>(ranked);
        merged.addAll(leftovers);

        normalizeScores(merged);

        return merged;
    }

    private static void normalizeScores(List<Map<String, Object>> items) {
        float maxScore = (float) items.stream()
                .filter(item -> item.get("score") != null)
                .mapToDouble(item -> ((Number) item.get("score")).floatValue())
                .max()
                .orElse(0.0);

        if (maxScore <= 0) return;

        for (Map<String, Object> item : items) {
            Object s = item.get("score");
            if (s != null) {
                item.put("score", ((Number) s).floatValue() / maxScore);
            }
        }
    }

    private static List<Map<String, Object>> localIndexSearch(String query, Logger logger, Directory index, String field) throws IOException {
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        BooleanQuery.Builder mainQuery = new BooleanQuery.Builder();

        String[] terms = query.toLowerCase().split("\\s+");

        Query q = queryBuilder(field, terms, mainQuery);


        TopDocs resultsTopDocs = searcher.search(q, Math.max(reader.numDocs(), 1));


        List<Map<String, Object>> newResults = new ArrayList<>();
        for (ScoreDoc scoreDoc : resultsTopDocs.scoreDocs) {
            Document foundDoc = searcher.doc(scoreDoc.doc);
            Map<String, Object> newMap = new HashMap<>();
            foundDoc.forEach(r -> newMap.put(r.name(), r.stringValue()));
            newMap.put("score", scoreDoc.score);
            newResults.add(newMap);
            logger.info("Score of: {} is {}", newMap.get("label"), scoreDoc.score);
        }
        reader.close();
        return newResults;
    }

    /*
        Define the local search result order/rank
     */
    private static Query queryBuilder(String field, String[] terms, BooleanQuery.Builder mainQuery) {
        if (terms.length == 0) {
            return mainQuery.build();
        }

        // Store original query terms exactly as entered
        String[] queryTerms = terms.clone();
        // Get lowercase versions for case-insensitive matching
        String[] lowerTerms = Arrays.stream(terms).map(String::toLowerCase).toArray(String[]::new);

        // Indices of "meaningful" (non-stopword) terms, used as anchors and for individual-term
        // boosting. Falls back to all terms if the query is made up entirely of stopwords.
        int[] meaningfulIdx = IntStream.range(0, terms.length)
                .filter(i -> !STOPWORDS.contains(lowerTerms[i]))
                .toArray();
        if (meaningfulIdx.length == 0) {
            meaningfulIdx = IntStream.range(0, terms.length).toArray();
        }
        int anchor = meaningfulIdx[0];

        // 1. Exact match of first meaningful query term at start (highest priority)
        Term exactQueryTerm = new Term(field, queryTerms[anchor]);
        PrefixQuery exactQueryPrefix = new PrefixQuery(exactQueryTerm);
        SpanQuery exactQuerySpan = new SpanMultiTermQueryWrapper<>(exactQueryPrefix);
        SpanFirstQuery exactQueryFirst = new SpanFirstQuery(exactQuerySpan, 1);
        mainQuery.add(new BoostQuery(exactQueryFirst, 200), BooleanClause.Occur.SHOULD);

        // 2. Exact match of query term anywhere
        TermQuery exactTermQuery = new TermQuery(exactQueryTerm);
        mainQuery.add(new BoostQuery(exactTermQuery, 150), BooleanClause.Occur.SHOULD);

        // 3. Case-insensitive prefix match at start
        Term lowerTerm = new Term(field + ".lowercase", lowerTerms[anchor]);
        PrefixQuery lowerPrefix = new PrefixQuery(lowerTerm);
        SpanQuery lowerSpan = new SpanMultiTermQueryWrapper<>(lowerPrefix);
        SpanFirstQuery lowerFirst = new SpanFirstQuery(lowerSpan, 1);
        mainQuery.add(new BoostQuery(lowerFirst, 50), BooleanClause.Occur.SHOULD);

        if (terms.length > 1) {
            // 4a. Exact full phrase at the START of the label (highest priority)
            SpanQuery[] spanTerms = new SpanQuery[terms.length];
            for (int i = 0; i < terms.length; i++) {
                spanTerms[i] = new SpanTermQuery(new Term(field, queryTerms[i]));
            }
            SpanNearQuery phraseSpan = new SpanNearQuery(spanTerms, 0, true);
            SpanFirstQuery phraseFirstQuery = new SpanFirstQuery(phraseSpan, terms.length);
            mainQuery.add(new BoostQuery(phraseFirstQuery, 1000), BooleanClause.Occur.SHOULD);

            // 4b. Exact phrase matches anywhere
            PhraseQuery.Builder phraseQuery = new PhraseQuery.Builder();
            for (int i = 0; i < terms.length; i++) {
                phraseQuery.add(new Term(field, queryTerms[i]), i);
            }
            mainQuery.add(new BoostQuery(phraseQuery.build(), 500), BooleanClause.Occur.SHOULD);

            // 5. Case-insensitive phrase matches
            PhraseQuery.Builder phraseLowerQuery = new PhraseQuery.Builder();
            for (int i = 0; i < terms.length; i++) {
                phraseLowerQuery.add(new Term(field + ".lowercase", lowerTerms[i]), i);
            }
            mainQuery.add(new BoostQuery(phraseLowerQuery.build(), 300), BooleanClause.Occur.SHOULD);
        }

        // 6. Individual term matches (stopwords excluded: they match too many unrelated labels
        // to be a meaningful relevance signal on their own)
        for (int i : meaningfulIdx) {
            // Exact case match of query terms
            TermQuery termQuery = new TermQuery(new Term(field, queryTerms[i]));
            mainQuery.add(new BoostQuery(termQuery, Math.max(30 - (i * 5), 10)), BooleanClause.Occur.SHOULD);

            // Case-insensitive matches
            TermQuery termLowerQuery = new TermQuery(new Term(field + ".lowercase", lowerTerms[i]));
            mainQuery.add(new BoostQuery(termLowerQuery, Math.max(20 - (i * 5), 5)), BooleanClause.Occur.SHOULD);
        }

        return mainQuery.build();
    }

    private static Directory indexResults(List<Map<String, Object>> combinedResults) throws IOException {
        Directory index = new ByteBuffersDirectory();

        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        IndexWriter w = new IndexWriter(index, config);


        for (Map<String, Object> result : combinedResults) {
            Document doc = new Document();
            String iri = (String) result.get("iri");
            String ontology = (String) result.getOrDefault("ontology", "");
            doc.add(new StringField("id", iri + "_" + ontology, Field.Store.YES));
            result.forEach((key, value) -> {
                doc.add(new TextField(key, String.valueOf(value), Field.Store.YES));
                doc.add(new TextField(key + ".lowercase", String.valueOf(value).toLowerCase(), Field.Store.YES));
            });
            w.addDocument(doc);
        }

        w.close();
        return index;
    }
}
