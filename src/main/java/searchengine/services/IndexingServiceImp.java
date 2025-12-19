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

        new Thread(() -> {
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
        }).start();
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
        Optional<SiteEntity> optionalSite = siteRepository.findAll()
                .stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .findFirst();

        SiteEntity siteEntity;

        if (optionalSite.isEmpty()) {
            Optional<Site> optionalSiteConfig = sitesList.getSites()
                    .stream()
                    .filter((site -> url.startsWith(site.getUrl())))
                    .findFirst();

            if (optionalSiteConfig.isEmpty()) {
                return false;
            }

            Site configSite = optionalSiteConfig.get();
            siteEntity = new SiteEntity();
            siteEntity.setUrl(configSite.getUrl());
            siteEntity.setName(configSite.getName());
            siteEntity.setStatus(StatusType.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
        } else {
            siteEntity = optionalSite.get();
        }

        String path = url.replace(siteEntity.getUrl(), "");
        if (path.isEmpty()) {
            path = "/";
        }
        PageEntity pageEntity;
        Optional<PageEntity> optionalPage = pageRepository.findByPathAndSiteId(path, siteEntity);

        if (optionalPage.isPresent()) {
            pageEntity = optionalPage.get();
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
        } else {
            pageEntity = new PageEntity();
            pageEntity.setSiteId(siteEntity);
            pageEntity.setPath(path);
        }

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

            Document document = null;
            if (statusCode == 200) {
                document = response.parse();
                pageEntity.setContent(document.outerHtml());
            } else {
                pageEntity.setContent("");
            }

            pageRepository.save(pageEntity);

            if (statusCode == 200) {
                lemmasCounter.countLemmas(document, siteEntity, pageEntity);
            }

            return true;
        } catch (Exception e) {
            // сетевые проблемы
            System.err.println("Ошибка загрузки: " + url + " — " + e.getMessage());
            pageEntity.setCode(500);
            pageEntity.setContent("");
            pageRepository.save(pageEntity);
            return false;
        }
    }

    @Override
    public boolean isIndexing() {
        return isIndexing;
    }

    @Override
    public boolean isSiteIndexed(String url) {
        Optional<SiteEntity> optionalSite = siteRepository.findByUrl(url);
        if (optionalSite.isEmpty()) {
            return false;
        } else {
            return optionalSite.get().getStatus() == StatusType.INDEXED;
        }
    }

    private void indexSite(Site site) {
        SiteEntity siteEntity = null;

        try {
            // 1. Удаляем старые данные
            siteCleanupService.clearSiteData(site.getUrl());

            // 2. Сохраняем новую запись в таблицу site
            saveSite(site.getUrl(), site.getName());

            // 3. Получаем сохранённую сущность
            siteEntity = siteRepository.findByUrl(site.getUrl()).orElseThrow();

            // 4. Индексируем страницу.
            siteEntity.setStatus(StatusType.INDEXING);
            pageParserImp.indexSitePages(siteEntity, forkJoinPool);

            // ПРОВЕРЯТЬ IS INDEXING
            // После успешного обхода ставим статус.
            if (isIndexing) {
                siteEntity.setStatus(StatusType.INDEXED);
                siteEntity.setStatusTime(LocalDateTime.now());
            } else {
                siteEntity.setStatus(StatusType.FAILED);
                siteEntity.setLastError("Индексация прервана пользователем");
            }
            siteRepository.save(siteEntity);


        } catch (Exception e) {
            // 5. Если ошибка — ставим FAILED и записываем текст ошибки
            if (siteEntity != null) {
                siteEntity.setStatus(StatusType.FAILED);
                siteEntity.setLastError("Ошибка при индексации сайта" + siteEntity.getUrl());
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }
            e.printStackTrace();
        }
    }

    private void clearSiteData(String siteUrl) {
        Optional<SiteEntity> site = siteRepository.findByUrl(siteUrl);
        if (site.isPresent()) {
            SiteEntity foundSite = site.get();
            indexRepository.deleteAllByPageId_SiteId(foundSite);
            lemmaRepository.deleteAllBySiteId(foundSite);
            pageRepository.deleteAllBySiteId(foundSite);
            siteRepository.delete(foundSite);
        }
    }

    private void saveSite(String siteUrl, String siteName) {
        Optional<SiteEntity> site = siteRepository.findByUrl(siteUrl);
        SiteEntity siteEntity;
        if (site.isPresent()) {
            siteEntity = site.get();
            siteEntity.setStatus(StatusType.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
        } else {
            siteEntity = new SiteEntity();
            siteEntity.setUrl(siteUrl);
            siteEntity.setName(siteName);
            siteEntity.setStatus(StatusType.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
        }
        siteRepository.save(siteEntity);
    }
}
