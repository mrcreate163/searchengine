package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.site.SiteRequest;
import searchengine.dto.site.SiteResponse;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String url);
    SiteResponse addSite(SiteRequest siteRequest);
    SiteResponse removeSite(String url);
}
