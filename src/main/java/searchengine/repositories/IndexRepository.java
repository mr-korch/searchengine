package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    List<IndexEntity> findAllByPageId(PageEntity pageEntity);

    void deleteAllByPageId_SiteId(SiteEntity siteEntity);

    List<PageEntity> findPagesByLemmaId(LemmaEntity lemma);

    Optional<IndexEntity> findByPageIdAndLemmaId(PageEntity pageEntity, LemmaEntity lemma);
}
