package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class PageParserImp {

    public void indexSitePages(SiteEntity siteEntity){
        new ForkJoinPool().invoke(new LinkRecursiveAction(siteEntity.getUrl(), siteEntity));
    }


}
