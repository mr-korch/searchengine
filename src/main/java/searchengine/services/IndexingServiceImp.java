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

@Service
@RequiredArgsConstructor
public class IndexingServiceImp implements IndexingService {

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final PageParserImp pageParserImp;

    private boolean isIndexing = false;

    @Override
    public boolean isIndexingStart() {
        if (isIndexing) {
            return false;
        }
        isIndexing = true;
        // Код индексации

        try {
            for (Site site : sitesList.getSites()) {
                indexSite(site);
            }
            return true;
        } finally {
            isIndexing = false;
        }
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
            pageParserImp.indexSitePages(siteEntity);

            // После успешного обхода ставим статус.
            siteEntity.setStatus(StatusType.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);


        } catch (Exception e) {
            // 5. Если ошибка — ставим FAILED и записываем текст ошибки
            if (siteEntity != null) {
                siteEntity.setStatus(StatusType.FAILED);
                siteEntity.setLastError("Ошибка при индексации сайта" + siteEntity.getUrl());
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }
        }
    }

    private void clearSiteData(String siteUrl) {
        Optional<SiteEntity> site = siteRepository.findByUrl(siteUrl);
        if (site.isPresent()) {
            SiteEntity foundSite = site.get();
            pageRepository.deleteAllBySiteId(foundSite.getId());
            siteRepository.delete(foundSite);
        }
        return;
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

//    private void indexPage(String siteUrl, SiteEntity siteEntity) {
//        try {
//            Document doc = Jsoup.connect(siteUrl).get();
//            int statusCode = Jsoup.connect(siteUrl).execute().statusCode();
//            String path = siteUrl.replace(siteEntity.getUrl(), "");
//
//            PageEntity pageEntity = new PageEntity();
//            pageEntity.setSiteId(siteEntity);
//            pageEntity.setContent(doc.html());
//            pageEntity.setCode(statusCode);
//            pageEntity.setPath(path);
//
//            pageRepository.save(pageEntity);
//        } catch (IOException e) {
//            siteEntity.setStatus(StatusType.FAILED);
//            siteEntity.setLastError("Ошибка загрузки: " + e.getMessage());
//            siteEntity.setStatusTime(LocalDateTime.now());
//            siteRepository.save(siteEntity);
//        }
//    }
}
