package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer> {

    Optional<LemmaEntity> findByLemmaAndSiteId(String lemma, SiteEntity siteEntity);

    Optional<LemmaEntity> findFirstByLemma(String lemma);

    Integer countBySiteId(SiteEntity siteEntity);

    void deleteAllBySiteId(SiteEntity siteEntity);

    @Query("SELECT SUM(le.frequency) FROM LemmaEntity le WHERE le.lemma = :lemma")
    Long sumFrequencyByLemma(@Param("lemma") String lemma);
}
