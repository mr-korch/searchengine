package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@Service
@RequiredArgsConstructor
public class PageParserImp {

    private final PageRepository pageRepository;
    private final Set<String> visited = ConcurrentHashMap.newKeySet();

    public void indexSitePages(SiteEntity siteEntity, ForkJoinPool pool) {
        pool.invoke(new LinkRecursiveAction(siteEntity.getUrl(), siteEntity));
    }

    private class LinkRecursiveAction extends RecursiveAction {
        private final String url;
        private final SiteEntity siteEntity;

        public LinkRecursiveAction(String url, SiteEntity siteEntity) {
            this.url = url;
            this.siteEntity = siteEntity;
        }

        @Override
        protected void compute() {

            String path = url.replaceFirst(siteEntity.getUrl(), "");
            if (path.isEmpty()) path = "/";

            if (visited.contains(url)) return;
            visited.add(url);

            if (pageRepository.findByPath(url).isPresent()) {
                return;
            }

            PageEntity pageEntity = new PageEntity();
            pageEntity.setSiteId(siteEntity);
            pageEntity.setPath(path);

            try {
                Thread.sleep(500 + (int) (Math.random() * 50));
                Document document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .ignoreContentType(true)
                        .referrer("http://www.google.com")
                        .get();

                pageEntity.setCode(200);
                pageEntity.setContent(document.outerHtml());

                pageRepository.save(pageEntity);

                Elements elements = document.select("a[href]");
                Set<LinkRecursiveAction> actionSet = new HashSet<>();

                for (Element element : elements) {
                    String link = element.absUrl("href");
                    if (link.isEmpty()) {
                        link = siteEntity.getUrl() + element.attr("href");
                    }
                    if (link.startsWith(siteEntity.getUrl())
                            && !link.contains("#")
                            && !link.contains("?")
                            && link.matches("https?://.+")) {
                        LinkRecursiveAction action = new LinkRecursiveAction(link, siteEntity);
                        action.fork();
                        actionSet.add(action);
                    }
                }

                for (LinkRecursiveAction action : actionSet) {
                    action.join();
                }

//            } catch (IOException e) {
//                System.err.println("Ошибка. Пропущена страница: " + url);
//            }
            } catch (IOException e) {
                // сетевые проблемы
                System.err.println("Ошибка загрузки: " + url + " — " + e.getMessage());
                pageEntity.setCode(404);
                pageEntity.setContent("");
                savePageAfterError(pageEntity);

            } catch (Exception e) {
                // любые другие
                System.err.println("Ошибка при обработке страницы: " + url + " — " + e.getMessage());
                pageEntity.setCode(500);
                pageEntity.setContent("");
                savePageAfterError(pageEntity);
            }
        }
    }

    private void savePageAfterError(PageEntity pageEntity) {
        try {
            pageRepository.save(pageEntity);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении страницы " + pageEntity.getPath() + ": " + e.getMessage());
        }
    }
}
