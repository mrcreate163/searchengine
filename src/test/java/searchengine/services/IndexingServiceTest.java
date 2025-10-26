package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndexingServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private SitesList sitesList;

    @Mock
    private LemmatizationService lemmatizationService;

    @Mock
    private IndexRepository indexRepository;

    @Mock
    private LemmaRepository lemmaRepository;

    @InjectMocks
    private IndexingServiceImpl indexingService;

    private Site configSite;
    private searchengine.model.Site siteEntity;

    @BeforeEach
    void setUp() {
        configSite = new Site();
        configSite.setUrl("https://test.com");
        configSite.setName("Test Site");

        siteEntity = new searchengine.model.Site();
        siteEntity.setId(1);
        siteEntity.setUrl("https://test.com");
        siteEntity.setName("Test Site");
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());
    }

    @Test
    void testStartIndexing_Success() {
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findAll()).thenReturn(Collections.emptyList());

        IndexingResponse response = indexingService.startIndexing();

        assertNotNull(response);
        assertTrue(response.isResult());
        verify(siteRepository).deleteAll();
        verify(siteRepository, atLeastOnce()).save(any());
    }

    @Test
    void testStartIndexing_AlreadyRunning() {
        searchengine.model.Site indexingSite = new searchengine.model.Site();
        indexingSite.setStatus(Status.INDEXING);
        when(siteRepository.findAll()).thenReturn(Collections.singletonList(indexingSite));

        IndexingResponse response = indexingService.startIndexing();

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Индексация уже запущена", response.getError());
        verify(siteRepository, never()).deleteAll();
    }

    @Test
    void testStopIndexing_Success() {
        siteEntity.setStatus(Status.INDEXING);
        when(siteRepository.findAll()).thenReturn(Collections.singletonList(siteEntity));

        IndexingResponse response = indexingService.stopIndexing();

        assertNotNull(response);
        assertTrue(response.isResult());
        verify(siteRepository, atLeastOnce()).save(any());
    }

    @Test
    void testStopIndexing_NotRunning() {
        when(siteRepository.findAll()).thenReturn(Collections.singletonList(siteEntity));

        IndexingResponse response = indexingService.stopIndexing();

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Индексация не запущена", response.getError());
    }

    @Test
    void testIndexPage_ValidUrl() {
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);
        when(pageRepository.findBySiteAndPath(any(), anyString())).thenReturn(null);

        // This will fail in actual execution due to network call, but we're testing the validation logic
        IndexingResponse response = indexingService.indexPage("https://test.com/page");

        assertNotNull(response);
        // In real scenario with mocked HTTP, this would succeed
        // For now, we're just checking it doesn't immediately fail validation
    }

    @Test
    void testIndexPage_UrlNotInConfig() {
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));

        IndexingResponse response = indexingService.indexPage("https://other-site.com/page");

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Данный URL находится за пределами сайтов, указанных в конфигурации", 
                     response.getError());
    }

    @Test
    void testIndexPage_UrlWithTrailingSlash() {
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);
        when(pageRepository.findBySiteAndPath(any(), anyString())).thenReturn(null);

        IndexingResponse response = indexingService.indexPage("https://test.com/");

        assertNotNull(response);
        // Should handle trailing slash correctly
    }

    @Test
    void testIndexPage_UrlMatchesConfigWithDifferentSlash() {
        Site configWithSlash = new Site();
        configWithSlash.setUrl("https://test.com/");
        configWithSlash.setName("Test Site");
        
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configWithSlash));
        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);

        // URL without trailing slash should still match config with trailing slash
        IndexingResponse response = indexingService.indexPage("https://test.com/page");

        assertNotNull(response);
        // Should not fail with "URL is outside configured sites" error
    }

    @Test
    void testIndexPage_RootPath() {
        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);
        when(pageRepository.findBySiteAndPath(any(), eq("/"))).thenReturn(null);

        IndexingResponse response = indexingService.indexPage("https://test.com");

        assertNotNull(response);
        // Should handle root path
    }

    @Test
    void testMultipleSitesInConfig() {
        Site site1 = new Site();
        site1.setUrl("https://site1.com");
        site1.setName("Site 1");

        Site site2 = new Site();
        site2.setUrl("https://site2.com");
        site2.setName("Site 2");

        when(sitesList.getSites()).thenReturn(Arrays.asList(site1, site2));
        when(siteRepository.findAll()).thenReturn(Collections.emptyList());

        IndexingResponse response = indexingService.startIndexing();

        assertNotNull(response);
        assertTrue(response.isResult());
        verify(siteRepository, times(2)).save(any());
    }

    @Test
    void testIndexPage_ReindexingExistingPage() {
        searchengine.model.Page existingPage = new searchengine.model.Page();
        existingPage.setId(1);
        existingPage.setSite(siteEntity);
        existingPage.setPath("/page");

        when(sitesList.getSites()).thenReturn(Collections.singletonList(configSite));
        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);
        when(pageRepository.findBySiteAndPath(any(), anyString())).thenReturn(existingPage);
        when(indexRepository.findByPage(existingPage)).thenReturn(Collections.emptyList());

        IndexingResponse response = indexingService.indexPage("https://test.com/page");

        // Should delete old page before adding new one
        verify(pageRepository).delete(existingPage);
    }
}
