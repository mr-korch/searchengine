package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.lemmas.LemmaFinder;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@Transactional
@RequiredArgsConstructor
public class LemmasCounter {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    public void countLemmas(Document document, SiteEntity siteEntity, PageEntity pageEntity) {
        try {
            LemmaFinder lemmaFinder = LemmaFinder.getInstance();
            String pageContentText = lemmaFinder.cleanHtmlTags(document);
            Map<String, Integer> foundLemmas = lemmaFinder.collectLemmas(pageContentText);

            for (Map.Entry<String, Integer> entry : foundLemmas.entrySet()) {
                String lemmaText = entry.getKey();
                Integer pageCount = entry.getValue();
                LemmaEntity lemmaEntity;
                Optional<LemmaEntity> optionalLemmaEntity = lemmaRepository.findByLemmaAndSiteId(lemmaText, siteEntity);

                if (optionalLemmaEntity.isPresent()) {
                    lemmaEntity = optionalLemmaEntity.get();
                    lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                } else {
                    lemmaEntity = new LemmaEntity();
                    lemmaEntity.setLemma(lemmaText);
                    lemmaEntity.setSiteId(siteEntity);
                    lemmaEntity.setFrequency(1);
                }
                lemmaRepository.save(lemmaEntity);

                IndexEntity indexEntity = new IndexEntity();
                indexEntity.setLemmaId(lemmaEntity);
                indexEntity.setPageId(pageEntity);
                indexEntity.setRankValue(pageCount);
                indexRepository.save(indexEntity);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
