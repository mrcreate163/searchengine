package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private SitesList sitesList;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private LemmaRepository lemmaRepository;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    private List<Site> configSites;
    private searchengine.model.Site siteEntity1;
    private searchengine.model.Site siteEntity2;

    @BeforeEach
    void setUp() {
        // Setup config sites
        Site site1 = new Site();
        site1.setUrl("https://site1.com");
        site1.setName("Site 1");

        Site site2 = new Site();
        site2.setUrl("https://site2.com");
        site2.setName("Site 2");

        configSites = Arrays.asList(site1, site2);

        // Setup entity sites
        siteEntity1 = new searchengine.model.Site();
        siteEntity1.setId(1);
        siteEntity1.setUrl("https://site1.com");
        siteEntity1.setName("Site 1");
        siteEntity1.setStatus(Status.INDEXED);
        siteEntity1.setStatusTime(LocalDateTime.now());

        siteEntity2 = new searchengine.model.Site();
        siteEntity2.setId(2);
        siteEntity2.setUrl("https://site2.com");
        siteEntity2.setName("Site 2");
        siteEntity2.setStatus(Status.INDEXING);
        siteEntity2.setStatusTime(LocalDateTime.now());
    }

    @Test
    void testGetStatistics_Success() {
        when(sitesList.getSites()).thenReturn(configSites);
        when(pageRepository.getTotalPages()).thenReturn(100);
        when(lemmaRepository.getTotalLemmas()).thenReturn(500L);
        when(siteRepository.findAll()).thenReturn(Arrays.asList(siteEntity1, siteEntity2));
        when(siteRepository.findFirstByUrl("https://site1.com")).thenReturn(siteEntity1);
        when(siteRepository.findFirstByUrl("https://site2.com")).thenReturn(siteEntity2);
        when(pageRepository.countBySite(siteEntity1)).thenReturn(50L);
        when(pageRepository.countBySite(siteEntity2)).thenReturn(50L);
        when(lemmaRepository.countBySite(siteEntity1)).thenReturn(250L);
        when(lemmaRepository.countBySite(siteEntity2)).thenReturn(250L);

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        assertTrue(response.isResult());
        assertNotNull(response.getStatistics());
        assertEquals(2, response.getStatistics().getTotal().getSites());
        assertEquals(100, response.getStatistics().getTotal().getPages());
        assertEquals(500, response.getStatistics().getTotal().getLemmas());
        assertTrue(response.getStatistics().getTotal().isIndexing());
        assertEquals(2, response.getStatistics().getDetailed().size());
    }

    @Test
    void testGetStatistics_WithIndexingSite() {
        when(sitesList.getSites()).thenReturn(configSites);
        when(pageRepository.getTotalPages()).thenReturn(100);
        when(lemmaRepository.getTotalLemmas()).thenReturn(500L);
        when(siteRepository.findAll()).thenReturn(Arrays.asList(siteEntity2)); // Only indexing site
        when(siteRepository.findFirstByUrl(anyString())).thenReturn(siteEntity2);
        when(pageRepository.countBySite(any())).thenReturn(50L);
        when(lemmaRepository.countBySite(any())).thenReturn(250L);

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        assertTrue(response.getStatistics().getTotal().isIndexing());
    }

    @Test
    void testGetStatistics_WithoutIndexingSite() {
        siteEntity2.setStatus(Status.INDEXED);
        when(sitesList.getSites()).thenReturn(configSites);
        when(pageRepository.getTotalPages()).thenReturn(100);
        when(lemmaRepository.getTotalLemmas()).thenReturn(500L);
        when(siteRepository.findAll()).thenReturn(Arrays.asList(siteEntity1, siteEntity2));
        when(siteRepository.findFirstByUrl(anyString())).thenReturn(siteEntity1);
        when(pageRepository.countBySite(any())).thenReturn(50L);
        when(lemmaRepository.countBySite(any())).thenReturn(250L);

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        assertFalse(response.getStatistics().getTotal().isIndexing());
    }

    @Test
    void testGetStatistics_SiteNotIndexed() {
        when(sitesList.getSites()).thenReturn(configSites);
        when(pageRepository.getTotalPages()).thenReturn(0);
        when(lemmaRepository.getTotalLemmas()).thenReturn(0L);
        when(siteRepository.findAll()).thenReturn(new ArrayList<>());
        when(siteRepository.findFirstByUrl(anyString())).thenReturn(null);

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        assertTrue(response.isResult());
        List<DetailedStatisticsItem> detailed = response.getStatistics().getDetailed();
        assertEquals(2, detailed.size());
        
        for (DetailedStatisticsItem item : detailed) {
            assertEquals("NOT_INDEXED", item.getStatus());
            assertEquals(0, item.getPages());
            assertEquals(0, item.getLemmas());
            assertEquals("", item.getError());
        }
    }

    @Test
    void testGetStatistics_WithError() {
        siteEntity1.setStatus(Status.FAILED);
        siteEntity1.setLastError("Connection timeout");
        
        when(sitesList.getSites()).thenReturn(Arrays.asList(configSites.get(0)));
        when(pageRepository.getTotalPages()).thenReturn(50);
        when(lemmaRepository.getTotalLemmas()).thenReturn(250L);
        when(siteRepository.findAll()).thenReturn(Arrays.asList(siteEntity1));
        when(siteRepository.findFirstByUrl("https://site1.com")).thenReturn(siteEntity1);
        when(pageRepository.countBySite(siteEntity1)).thenReturn(50L);
        when(lemmaRepository.countBySite(siteEntity1)).thenReturn(250L);

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        DetailedStatisticsItem item = response.getStatistics().getDetailed().get(0);
        assertEquals("FAILED", item.getStatus());
        assertEquals("Connection timeout", item.getError());
    }

    @Test
    void testGetStatistics_ZeroSites() {
        when(sitesList.getSites()).thenReturn(new ArrayList<>());
        when(pageRepository.getTotalPages()).thenReturn(0);
        when(lemmaRepository.getTotalLemmas()).thenReturn(0L);
        when(siteRepository.findAll()).thenReturn(new ArrayList<>());

        StatisticsResponse response = statisticsService.getStatistics();

        assertNotNull(response);
        assertTrue(response.isResult());
        assertEquals(0, response.getStatistics().getTotal().getSites());
        assertEquals(0, response.getStatistics().getDetailed().size());
    }
}
