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
    private static final Double FREQUENCY_THRESHOLD = 0.75;

    public SearchResponse search(String query, String site, int offset, int limit) {
        List<String> lemmasFromQuery = collectLemmas(query);

        SiteEntity siteEntity = (site == null) ? null : siteRepository.findByUrl(site).orElseThrow();

        Map<String, Long> filteredLemmas = filterLemmasByFrequency(lemmasFromQuery, siteEntity);
        if (filteredLemmas.isEmpty()) {
            return new SearchResponse(true, 0, List.of());
        }

        List<PageEntity> matchingPages = findMatchingPages(filteredLemmas, siteEntity);

        List<SearchResult> results = getSearchResponse(matchingPages, filteredLemmas, siteEntity, query);

        results.sort((a, b) -> Double.compare(b.getRelevance(), a.getRelevance()));

        int start = Math.max(offset, 0);
        int finish = Math.min(start + Math.max(limit, 0), results.size());

        if (start >= results.size()) {
            return new SearchResponse(true, results.size(), List.of());
        }

        return new SearchResponse(true, results.size(), results.subList(start, finish));
    }


    private List<String> collectLemmas(String query) {
        try {
            return LemmaFinder
                    .getInstance()
                    .collectLemmas(query)
                    .keySet()
                    .stream()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка получения лемм", e);
        }
    }

    private Map<String, Long> filterLemmasByFrequency(List<String> lemmas, SiteEntity siteEntity) {
        long totalPages = (siteEntity == null) ?
                pageRepository.count()
                : pageRepository.countBySiteId(siteEntity);

        long oftenLevel = (long) (totalPages * FREQUENCY_THRESHOLD);
        Map<String, Long> filteredLemmas = new HashMap<>();

        long freq;

        for (String lemma : lemmas) {
            if (siteEntity == null) {
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

        return filteredLemmas.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    private List<PageEntity> findMatchingPages(Map<String, Long> filteredLemmas, SiteEntity siteEntity) {
        List<PageEntity> matchingPages = new ArrayList<>();
        boolean firstLemma = true;

        for (Map.Entry<String, Long> lemma : filteredLemmas.entrySet()) {
            Optional<LemmaEntity> lemmaEntity = (siteEntity == null)
                    ? lemmaRepository.findFirstByLemma(lemma.getKey())
                    : lemmaRepository.findByLemmaAndSiteId(lemma.getKey(), siteEntity);

            if (lemmaEntity.isEmpty()) {
                return List.of();
            }

            List<PageEntity> pagesWithLemma = indexRepository.findPagesByLemmaId(lemmaEntity.get());

            if (firstLemma) {
                matchingPages.addAll(pagesWithLemma);
                firstLemma = false;
            } else {
                matchingPages.retainAll(pagesWithLemma);
            }
            if (matchingPages.isEmpty()) {
                return List.of();
            }
        }

        return matchingPages;
    }

    private List<SearchResult> getSearchResponse(List<PageEntity> matchingPages, Map<String, Long> filteredLemmas,
                                                 SiteEntity siteEntity, String query) {

        Map<PageEntity, Double> absRelevanceMap = new HashMap<>();
        double maxAbsRel = 0;

        for (PageEntity pageEntity : matchingPages) {
            double absRel = 0;

            for (String lemmaText : filteredLemmas.keySet()) {
                Optional<LemmaEntity> lemmaEntity = siteEntity == null
                        ? lemmaRepository.findFirstByLemma(lemmaText)
                        : lemmaRepository.findByLemmaAndSiteId(lemmaText, siteEntity);

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
                    SnippetBuilder.getSnippet(content, query, filteredLemmas),
                    relRel));
        }

        return results;
    }
}