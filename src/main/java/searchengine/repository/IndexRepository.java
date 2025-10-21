package searchengine.repository;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Index;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends CrudRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    void deleteByPage(Page page);
}
