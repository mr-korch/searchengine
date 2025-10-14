package searchengine.services;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@Service
public class LinkRecursiveAction extends RecursiveAction {

    private final String url;
    private final SiteEntity siteEntity;
    private PageRepository pageRepository;
    private final Set<String> visited = new HashSet<>();

    public LinkRecursiveAction(String url, SiteEntity siteEntity) {
        this.url = url;
        this.siteEntity = siteEntity;
    }

    @Override
    protected void compute() {

        synchronized (visited) {
            if (visited.contains(url)) {
                return;
            }
            visited.add(url);
        }

        try {
            Thread.sleep(100 + (int) (Math.random() * 50));
            Document document = Jsoup.connect(url).ignoreContentType(true).get();

            PageEntity pageEntity = new PageEntity();
            pageEntity.setSiteId(siteEntity);
            pageEntity.setPath(url);
            pageEntity.setCode(200);
            pageEntity.setContent(document.outerHtml());
            pageRepository.save(pageEntity);

            Elements elements = document.select("a[href]");
            Set<LinkRecursiveAction> subActions = new HashSet<>();


            for (Element element : elements) {
                String link = element.absUrl("href");
                if (link.startsWith(url) && !link.contains("#") && !link.contains("?") && !visited.contains(link)) {
                    LinkRecursiveAction action = new LinkRecursiveAction(link, siteEntity);
                    action.fork();
                    subActions.add(action);

                }
            }

            for (LinkRecursiveAction action : subActions){
                action.join();
            }

        } catch (InterruptedException | IOException e) {
            System.err.println("Ошибка. Пропущена страница: " + url);
        }


    }
}
