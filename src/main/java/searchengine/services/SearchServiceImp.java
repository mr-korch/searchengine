package searchengine.services;

import lombok.RequiredArgsConstructor;
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

    public SearchResponse search(String query, String site) {
        List<String> lemmas;
        try {
            lemmas = LemmaFinder.getInstance()
                    .collectLemmas(query)
                    .keySet()
                    .stream()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Ошибка получения лемм", e);
        }

        long totalPages = (site == null)
                ? pageRepository.count()
                : pageRepository.countBySiteId(siteRepository.findByUrl(site).orElseThrow());

        long oftenLevel = (long) (totalPages * 0.65);
        long freq;
        Map<String, Long> filteredLemmas = new HashMap<>();

        for (String lemma : lemmas) {
            if (site == null) {
                freq = lemmaRepository.sumFrequencyByLemma(lemma);
            } else {
                SiteEntity siteEntity = siteRepository.findByUrl(site).orElseThrow();
                LemmaEntity lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma, siteEntity).orElseThrow();
                freq = lemmaEntity.getFrequency();
            }
            if (freq < oftenLevel) {
                filteredLemmas.put(lemma, freq);
            }
        }

        Map<String, Long> sortedLemmas = filteredLemmas.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (x, y) -> x,
                        LinkedHashMap::new));


        List<PageEntity> matchingPages = new ArrayList<>();
        boolean firstLemma = true;
        for (Map.Entry<String, Long> lemma : sortedLemmas.entrySet()) {
            LemmaEntity lemmaEntity;
            if (site != null) {
                SiteEntity siteEntity = siteRepository.findByUrl(site).orElseThrow();
                lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemma.getKey(), siteEntity).orElseThrow();
            } else {
                lemmaEntity = lemmaRepository.findFirstByLemma(lemma.getKey()).orElse(null);
            }

            if (lemmaEntity == null) {
                continue;
            }

            // НАХОДИМ СТРАНИЦЫ С ЭТОЙ ЛЕММОЙ
            List<PageEntity> pagesWithLemma = indexRepository.findPagesByLemmaId(lemmaEntity);

            if (firstLemma) {
                // на первой лемме просто записываем все страницы
                matchingPages.addAll(pagesWithLemma);
                firstLemma = false;
            } else {
                // на следующих – ищем пересечение со старыми
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
                LemmaEntity lemmaEntity;
                if (site != null) {
                    SiteEntity siteEntity = siteRepository.findByUrl(site).orElseThrow();
                    lemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemmaText, siteEntity).orElseThrow();
                } else {
                    lemmaEntity = lemmaRepository.findFirstByLemma(lemmaText).orElse(null);
                }
                if (lemmaEntity == null) {
                    continue;
                }

                Optional<IndexEntity> indexOpt = indexRepository.findByPageIdAndLemmaId(pageEntity, lemmaEntity);
                if (indexOpt.isPresent()) {
                    absRel += indexOpt.get().getRankValue();
                }

                absRelevanceMap.put(pageEntity, absRel);

                if (absRel > maxAbsRel) {
                    maxAbsRel = absRel;
                }
            }
        }

        // ВЫЧИСЛЕНИЕ ОТНОСИТЕЛЬНОЙ РЕЛЕВАНТОСТИ.
        List<SearchResult> results = new ArrayList<>();
        for (PageEntity pageEntity : matchingPages) {
            double absRel = absRelevanceMap.get(pageEntity);
            double relRel = absRel / maxAbsRel;
            results.add(new SearchResult(
                    pageEntity.getSiteId().getUrl(),
                    pageEntity.getSiteId().getName(),
                    pageEntity.getPath(),
                    "",
                    relRel
                    ));
        }

        results.sort((a,b) -> Double.compare(b.getRelevance(), a.getRelevance()));
        return new SearchResponse(true, results.size(), results);
    }
}
