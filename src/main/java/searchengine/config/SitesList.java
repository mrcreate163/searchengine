package searchengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "indexing-settings")
public class SitesList {
    private List<Site> sites = new ArrayList<>();
    
    /**
     * Add a site dynamically at runtime
     */
    public synchronized void addSite(Site site) {
        // Check if site with same URL already exists
        if (sites.stream().noneMatch(s -> s.getUrl().equals(site.getUrl()))) {
            sites.add(site);
        }
    }
    
    /**
     * Remove a site by URL
     */
    public synchronized boolean removeSite(String url) {
        return sites.removeIf(site -> site.getUrl().equals(url));
    }
}
