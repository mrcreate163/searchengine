package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

@Repository
public interface PageRepository extends JpaRepository<Page, Integer> {

    long countBySite(Site site);
    Page findBySiteAndPath(Site site, String path);

    @Query("select count(p) from Page p")
    int getTotalPages();
}
