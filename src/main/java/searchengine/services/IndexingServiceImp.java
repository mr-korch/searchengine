package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.SiteEntity;
import searchengine.model.StatusType;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImp implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PageParserImp pageParserImp;

    private volatile boolean isIndexing = false;
    private ForkJoinPool forkJoinPool;

    @Override
    public boolean startIndexing() {
        if (isIndexing) {
            return false;
        }
        isIndexing = true;
        forkJoinPool = new ForkJoinPool();


        new Thread(() -> {
            try {
                for (Site site : sitesList.getSites()) {
                    if (!isIndexing) {
                        break;
                    }
                    forkJoinPool.execute(() -> indexSite(site));
                }
            } catch (Exception e) {
                e.printStackTrace();
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

    private void indexSite(Site site) {
        SiteEntity siteEntity = null;

        try {
            // 1. Удаляем старые данные
            clearSiteData(site.getUrl());

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
