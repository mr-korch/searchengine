package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class IndexingServiceImp implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageParserImp pageParserImp;
    private final LemmasCounter lemmasCounter;
    private final SiteCleanupService siteCleanupService;


    private volatile boolean isIndexing = false;
    private ForkJoinPool forkJoinPool;

    @Override
    public boolean startIndexing() {
        if (isIndexing) {
            return false;
        }
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();
        pageParserImp.clearVisited();

        new Thread(this::runIndexing).start();
        return true;
    }

    @Override
    public boolean stopIndexing() {
        if (!isIndexing) {
            return false;
        }
        isIndexing = false;
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow();
        }

        for (Site site : sitesList.getSites()) {
            SiteEntity siteEntity = siteRepository.findByUrl(site.getUrl()).orElseThrow();
            if (siteEntity.getStatus().equals(StatusType.INDEXING)) {
                siteEntity.setStatus(StatusType.FAILED);
                siteEntity.setLastError("Индексация прервана пользователем");
                siteRepository.save(siteEntity);
            }
        }
        return true;
    }

    @Override
    public boolean indexPage(String url) {

        SiteEntity siteEntity = getSiteEntity(url);
        if (siteEntity == null) {
            return false;
        }

        String path = getPagePath(siteEntity.getUrl(), url);
        PageEntity pageEntity = getPage(siteEntity, path);

        return loadAndIndexPage(siteEntity, pageEntity, url);
    }

    @Override
    public boolean isIndexing() {
        return isIndexing;
    }

    @Override
    public boolean isSiteIndexed(String url) {
        return siteRepository.findByUrl(url)
                .map(site -> site.getStatus() == StatusType.INDEXED)
                .orElse(false);
    }

    private void runIndexing() {
        try {
            for (Site site : sitesList.getSites()) {
                if (!isIndexing) {
                    break;
                }
                forkJoinPool.execute(() -> indexSite(site));
            }
            forkJoinPool.shutdown();
            forkJoinPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isIndexing = false;
        }
    }

    private SiteEntity getSiteEntity(String url) {
        Optional<SiteEntity> optionalSite = siteRepository.findAll()
                .stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .findFirst();

        if (optionalSite.isPresent()) {
            return optionalSite.get();
        } else {
            Optional<Site> optionalConfigSite = sitesList.getSites()
                    .stream()
                    .filter(site -> url.startsWith(site.getUrl()))
                    .findFirst();

            if (optionalConfigSite.isEmpty()) {
                return null;
            }

            Site site = optionalConfigSite.get();

            SiteEntity siteEntity = new SiteEntity();
            siteEntity.setUrl(site.getUrl());
            siteEntity.setName(site.getName());
            siteEntity.setStatus(StatusType.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            return siteRepository.save(siteEntity);
        }
    }

    private String getPagePath(String siteUrl, String pageUrl) {
        String path = pageUrl.replace(siteUrl, "");
        return path.isEmpty() ? "/" : path;
    }

    private PageEntity getPage(SiteEntity siteEntity, String path) {
        Optional<PageEntity> optionalPage = pageRepository.findByPathAndSiteId(path, siteEntity);
        if (optionalPage.isEmpty()) {
            PageEntity pageEntity = new PageEntity();
            pageEntity.setSiteId(siteEntity);
            pageEntity.setPath(path);
            return pageEntity;
        }

        PageEntity pageEntity = optionalPage.get();

        List<IndexEntity> indexEntityList = indexRepository.findAllByPageId(pageEntity);
        for (IndexEntity index : indexEntityList) {
            LemmaEntity lemma = index.getLemmaId();
            lemma.setFrequency(lemma.getFrequency() - 1);

            if (lemma.getFrequency() == 0) {
                lemmaRepository.delete(lemma);
            } else {
                lemmaRepository.save(lemma);
            }

            indexRepository.delete(index);
        }
        pageRepository.delete(pageEntity);

        PageEntity newPage = new PageEntity();
        newPage.setSiteId(siteEntity);
        newPage.setPath(path);

        return newPage;
    }

    private boolean loadAndIndexPage(SiteEntity siteEntity, PageEntity pageEntity, String url) {
        Connection.Response response;
        try {
            response = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
                    .referrer("https://www.google.com")
                    .ignoreHttpErrors(true)
                    .followRedirects(true)
                    .execute();

            int statusCode = response.statusCode();
            pageEntity.setCode(statusCode);

            Document document;
            if (statusCode == 200) {
                document = response.parse();
                pageEntity.setContent(document.outerHtml());
                pageRepository.save(pageEntity);
                lemmasCounter.countLemmas(document, siteEntity, pageEntity);
            } else {
                pageEntity.setContent("");
                pageRepository.save(pageEntity);
            }

            return true;

        } catch (Exception e) {
            pageEntity.setCode(500);
            pageEntity.setContent("");
            pageRepository.save(pageEntity);

            return false;
        }
    }

    private void indexSite(Site site) {
        SiteEntity siteEntity = null;

        try {
            siteCleanupService.clearSiteData(site.getUrl());
            siteEntity = createOrUpdateSite(site);
            pageParserImp.indexSitePages(siteEntity, forkJoinPool);
            setSiteStatus(siteEntity);
        } catch (Exception exception) {
            handleError(siteEntity, exception);
        }

    }

    private SiteEntity createOrUpdateSite(Site site) {
        SiteEntity siteEntity;
        Optional<SiteEntity> siteEntityOpt = siteRepository.findByUrl(site.getUrl());
        if (siteEntityOpt.isPresent()) {
            siteEntity = siteEntityOpt.get();
        } else {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(site.getUrl());
            siteEntity.setName(site.getName());
        }
        siteEntity.setStatus(StatusType.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());

        return siteRepository.save(siteEntity);
    }

    private void setSiteStatus(SiteEntity siteEntity) {
        if (isIndexing) {
            siteEntity.setStatus(StatusType.INDEXED);
        } else {
            siteEntity.setStatus(StatusType.FAILED);
            siteEntity.setLastError("Индексация прервана пользователем");
        }
        siteEntity.setStatusTime(LocalDateTime.now());
        siteRepository.save(siteEntity);
    }

    private void handleError(SiteEntity siteEntity, Exception exception) {
        if (siteEntity != null) {
            siteEntity.setStatus(StatusType.FAILED);
            siteEntity.setLastError("Ошибка при индексации сайта" + siteEntity.getUrl());
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        }
        exception.printStackTrace();
    }
}
