package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findBySiteAndLemma(Site site, String lemma);

    long countBySite(Site site);

    @Query("select count(l) from Lemma l")
    long getTotalLemmas();

    @Query("select l from Lemma l where l.lemma in :lemmas and l.site = :site order by l.frequency asc")
    List<Lemma> findLemmasBySiteAndLemmaIn(@Param("site") Site site,@Param("lemmas") List<String> lemmas);

    @Query("select l from Lemma l where l.lemma in :lemmas order by l.frequency asc")
    List<Lemma> findLemmaByLemmaIn(@Param("lemmas") List<String> lemmas);

    @Query("select count(l) from Lemma l where l.site = :site")
    long countTotalLemmasBySite(@Param("site") Site site);
}
