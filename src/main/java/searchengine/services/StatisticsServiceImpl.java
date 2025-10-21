package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;


    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setPages(pageRepository.getTotalPages());
        total.setIndexing(isIndexingRunning());
        total.setLemmas((int) lemmaRepository.getTotalLemmas());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        for (Site configSite : sites.getSites()) {
            searchengine.model.Site siteEntity = siteRepository.findFirstByUrl(configSite.getUrl());

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(configSite.getName());
            item.setUrl(configSite.getUrl());

            if (siteEntity != null) {
                item.setStatus(siteEntity.getStatus().toString());
                item.setStatusTime(siteEntity.getStatusTime());
                item.setError(siteEntity.getLastError() != null ? siteEntity.getLastError() : "");
                item.setPages(pageRepository.countBySite(siteEntity));
                item.setLemmas(lemmaRepository.countBySite(siteEntity));
            } else  {
                item.setStatus("NOT_INDEXED");
                item.setStatusTime(LocalDateTime.now());
                item.setError("");
                item.setPages(0);
                item.setLemmas(0);
            }

            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        data.setIndexing(total.isIndexing());

        response.setResult(true);
        response.setStatistics(data);
        return response;
    }

    private boolean isIndexingRunning() {
        return siteRepository.findAll().stream()
                .anyMatch(s -> s.getStatus().equals(Status.INDEXING));
    }
}
