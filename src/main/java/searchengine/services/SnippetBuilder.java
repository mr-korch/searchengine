package searchengine.services;

import org.jsoup.Jsoup;
import searchengine.lemmas.LemmaFinder;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class SnippetBuilder {

    private static final int SNIPPET_RADIUS = 150;

    public static String getSnippet(String html, String query, Map<String, Long> sortedLemmas) {
        if (html == null || html.isBlank() || query == null || query.isBlank()) {
            return "";
        }

        String text = Jsoup.parse(html).text();
        if (text.isBlank()) {
            return "";
        }

        String[] textWords = text.split("\\s+");
        String lemmaFromText;
        LemmaFinder lemmaFinder = null;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int pos = -1;
        for (String word : textWords) {

            Set<String> set = lemmaFinder.getLemmaSet(word);
            if (!set.isEmpty()) {
                lemmaFromText = set.iterator().next();
                for (String lemmaFromSorted : sortedLemmas.keySet()) {
                    if (lemmaFromText.equals(lemmaFromSorted)) {
                        pos = text.toLowerCase().indexOf(word.toLowerCase());
                        break;
                    }
                }
            }
        }

        if (pos == -1)
            return "";

        int start = Math.max(0, pos - SNIPPET_RADIUS);
        int end = Math.min(text.length(), pos + SNIPPET_RADIUS);
        String snippet = text.substring(start, end);

        if (start > 100) snippet = "..." + snippet;
        if (end < text.length() - 200) snippet = snippet + "...";

        return highlightLemma(snippet, sortedLemmas.keySet());
    }

    private static String highlightLemma(String snippet, Set<String> sortedLemmas) {
        String[] words = snippet.split("\\s");
        StringBuilder result = new StringBuilder();
        Set<String> lemmaSet;
        for (String word : words) {
            try {
                lemmaSet = LemmaFinder.getInstance().getLemmaSet(word);
                if (!lemmaSet.isEmpty()) {
                    if (sortedLemmas.contains(LemmaFinder.getInstance().getLemmaSet(word).iterator().next().toLowerCase())) {
                        result.append("<b>").append(word).append("</b> ");
                    } else {
                        result.append(word).append(" ");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }
}
