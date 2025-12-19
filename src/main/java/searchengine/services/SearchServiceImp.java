package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.responces.SearchResponse;
import searchengine.dto.responces.SearchResult;
import searchengine.lemmas.LemmaFinder;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImp implements SearchService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Double FREQ_COEF = 0.75;

    public SearchResponse search(String query, String site, int offest, int limit) {
        List<String> lemmasFromQuery;
        try {
            lemmasFromQuery = LemmaFinder.getInstance().collectLemmas(query).keySet().stream().toList();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка получения лемм", e);
        }

        long totalPages = (site == null) ?
                pageRepository.count()
                : pageRepository.countBySiteId(siteRepository.findByUrl(site).orElseThrow());

        long oftenLevel = (long) (totalPages * FREQ_COEF);
        long freq;
        Map<String, Long> filteredLemmas = new HashMap<>();
        SiteEntity siteEntity = null;

        if (site != null) {
            siteEntity = siteRepository.findByUrl(site).orElseThrow();
        }

        for (String lemma : lemmasFromQuery) {
            if (site == null) {
                Long sum = lemmaRepository.sumFrequencyByLemma(lemma);
                freq = (sum == null) ? 0 : sum;
            } else {
                Optional<LemmaEntity> lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteEntity);
                if (lemmaEntity.isEmpty()) {
                    continue;
                }
                freq = lemmaEntity.get().getFrequency();
            }
            if (freq < oftenLevel) {
                filteredLemmas.put(lemma, freq);
            }
        }

        if (filteredLemmas.isEmpty()) {
            return new SearchResponse(true, 0, List.of());
        }

        Map<String, Long> sortedLemmas = filteredLemmas
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x, LinkedHashMap::new));

        List<PageEntity> matchingPages = new ArrayList<>();
        boolean firstLemma = true;
        for (Map.Entry<String, Long> lemma : sortedLemmas.entrySet()) {
            Optional<LemmaEntity> lemmaEntity;
            if (site != null) {
                lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma.getKey(), siteEntity);
            } else {
                lemmaEntity = lemmaRepository.findFirstByLemma(lemma.getKey());
            }
            if (lemmaEntity.isEmpty()) {
                continue;
            }

            // НАХОДИМ СТРАНИЦЫ С ЭТОЙ ЛЕММОЙ
            List<PageEntity> pagesWithLemma = indexRepository.findPagesByLemmaId(lemmaEntity.get());
            if (firstLemma) {
                matchingPages.addAll(pagesWithLemma);
                firstLemma = false;
            } else {
                matchingPages.retainAll(pagesWithLemma);
            }
            if (matchingPages.isEmpty()) {
                return new SearchResponse(true, 0, List.of());
            }
        }

        // РАСЧЕТ РЕЛЕВАНТНОСТИ
        Map<PageEntity, Double> absRelevanceMap = new HashMap<>();
        double maxAbsRel = 0;
        for (PageEntity pageEntity : matchingPages) {
            double absRel = 0;
            for (String lemmaText : sortedLemmas.keySet()) {
                Optional<LemmaEntity> lemmaEntity;
                if (site != null) {
                    lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemmaText, siteEntity);
                } else {
                    lemmaEntity = lemmaRepository.findFirstByLemma(lemmaText);
                }
                if (lemmaEntity.isEmpty()) {
                    continue;
                }
                Optional<IndexEntity> indexOpt = indexRepository.findByPageIdAndLemmaId(pageEntity, lemmaEntity.get());
                if (indexOpt.isPresent()) {
                    absRel += indexOpt.get().getRankValue();
                }
            }
            absRelevanceMap.put(pageEntity, absRel);
            if (absRel > maxAbsRel) {
                maxAbsRel = absRel;
            }
        }

        // ВЫЧИСЛЕНИЕ ОТНОСИТЕЛЬНОЙ РЕЛЕВАНТОСТИ.
        List<SearchResult> results = new ArrayList<>();
        for (PageEntity pageEntity : matchingPages) {

            double absRel = absRelevanceMap.get(pageEntity);
            double relRel = absRel / maxAbsRel; // не может быть деления на 0

            String title = "";
            String content = pageEntity.getContent();

            if (content != null && !content.isBlank()) {
                title = Jsoup.parse(content).title();
            }

            results.add(new SearchResult(
                    pageEntity.getSiteId().getUrl(),
                    pageEntity.getSiteId().getName(),
                    pageEntity.getPath(),
                    title,
                    SnippetBuilder.getSnippet(content, query, sortedLemmas),
                    relRel));
        }

        results.sort((a, b) -> Double.compare(b.getRelevance(), a.getRelevance()));

        int maxIndex = Math.min(results.size(), Math.max(limit, 0));
        List<SearchResult> limitResult = results.subList(0, maxIndex);
        return new SearchResponse(true, results.size(), limitResult);
    }
}