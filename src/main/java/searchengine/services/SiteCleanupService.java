package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

@Service
@RequiredArgsConstructor
public class SiteCleanupService {

    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;

    @Transactional
    public void clearSiteData(String siteUrl) {
        SiteEntity site = siteRepository.findByUrl(siteUrl).orElse(null);
        if (site == null) {
            return;
        }
        indexRepository.deleteAllByPageId_SiteId(site);
        lemmaRepository.deleteAllBySiteId(site);
        pageRepository.deleteAllBySiteId(site);
        siteRepository.delete(site);
    }
}