package searchengine.lemmas;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {

    private final LuceneMorphology luceneMorphology;
    private static LemmaFinder instance;
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};

    private LemmaFinder() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology(); // тяжелая загрузка словаря
    }

    public static synchronized LemmaFinder getInstance() throws IOException {
        if (instance == null) {
            instance = new LemmaFinder();
        }
        return instance;
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    public Map<String, Integer> collectLemmas(String text) {
        String[] words = text.toLowerCase().replaceAll("[^а-яА-Я\\s]", "").trim().split("\\s+");
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {

            // Проверка слову на пустоту.
            if (word.isBlank()) {
                continue;
            }

            // Проверка, является ли слово междометием и т.п.
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }

            // Проверка, есть ли у слова нормальная форма.
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0).toLowerCase();

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }

    /**
     * @param text текст из которого собираем все леммы
     * @return набор уникальных лемм найденных в тексте
     */
    public Set<String> getLemmaSet(String text) {
        String[] textArray = text.toLowerCase().replaceAll("[^а-яА-Я\\s]", "").trim().split("\\s+");
        Set<String> lemmaSet = new HashSet<>();
        for (String word : textArray) {
            if (!word.isEmpty() && isCorrectWordForm(word)) {
                List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
                if (anyWordBaseBelongToParticle(wordBaseForms)) {
                    continue;
                }
                lemmaSet.addAll(luceneMorphology.getNormalForms(word));
            }
        }
        return lemmaSet;
    }

    public String cleanHtmlTags(Document document) {
        if (document == null)
            return "";

        return document.body().text();
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        for (String wordBaseForm : wordBaseForms) {
            if (hasParticleProperty(wordBaseForm)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCorrectWordForm(String word) {
        return !word.isBlank();
    }
}


