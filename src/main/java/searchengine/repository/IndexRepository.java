package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    
    @Modifying
    @Transactional
    void deleteByPage(Page page);

    @Query("select i from Index i where i.lemma in :lemmas")
    List<Index> findByLemmaIn(@Param("lemmas") List<Lemma> lemmas);

    @Query("select i from Index i where i.page = :page and i.lemma in :lemmas")
    List<Index> findByPageAndLemmaIn(@Param("page") Page page, @Param("lemmas") List<Lemma> lemmas);

    boolean existsByPageAndLemma(Page page, Lemma lemma);
}
