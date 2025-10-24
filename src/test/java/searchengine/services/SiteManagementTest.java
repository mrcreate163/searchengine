package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.site.SiteRequest;
import searchengine.dto.site.SiteResponse;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SiteManagementTest {

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

    private List<Site> mockSitesList;

    @BeforeEach
    void setUp() {
        mockSitesList = new ArrayList<>();
        lenient().when(sitesList.getSites()).thenReturn(mockSitesList);
    }

    @Test
    void testAddSite_Success() {
        SiteRequest request = new SiteRequest();
        request.setUrl("https://newsite.com");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertTrue(response.isResult());
        assertNull(response.getError());
        verify(sitesList).addSite(any(Site.class));
    }

    @Test
    void testAddSite_WithTrailingSlash() {
        SiteRequest request = new SiteRequest();
        request.setUrl("https://newsite.com/");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertTrue(response.isResult());
        verify(sitesList).addSite(argThat(site -> 
            site.getUrl().equals("https://newsite.com") && 
            !site.getUrl().endsWith("/")
        ));
    }

    @Test
    void testAddSite_EmptyUrl() {
        SiteRequest request = new SiteRequest();
        request.setUrl("");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("URL не может быть пустым", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_NullUrl() {
        SiteRequest request = new SiteRequest();
        request.setUrl(null);
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("URL не может быть пустым", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_EmptyName() {
        SiteRequest request = new SiteRequest();
        request.setUrl("https://newsite.com");
        request.setName("");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Название сайта не может быть пустым", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_NullName() {
        SiteRequest request = new SiteRequest();
        request.setUrl("https://newsite.com");
        request.setName(null);

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Название сайта не может быть пустым", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_InvalidUrlNoProtocol() {
        SiteRequest request = new SiteRequest();
        request.setUrl("newsite.com");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("URL должен начинаться с http:// или https://", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_DuplicateUrl() {
        Site existingSite = new Site();
        existingSite.setUrl("https://existingsite.com");
        existingSite.setName("Existing Site");
        mockSitesList.add(existingSite);

        SiteRequest request = new SiteRequest();
        request.setUrl("https://existingsite.com");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Сайт с таким URL уже существует в конфигурации", response.getError());
        verify(sitesList, never()).addSite(any());
    }

    @Test
    void testAddSite_DuplicateUrlWithTrailingSlash() {
        Site existingSite = new Site();
        existingSite.setUrl("https://existingsite.com/");
        existingSite.setName("Existing Site");
        mockSitesList.add(existingSite);

        SiteRequest request = new SiteRequest();
        request.setUrl("https://existingsite.com");
        request.setName("New Site");

        SiteResponse response = indexingService.addSite(request);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Сайт с таким URL уже существует в конфигурации", response.getError());
    }

    @Test
    void testRemoveSite_Success() {
        when(sitesList.removeSite(anyString())).thenReturn(true);
        when(siteRepository.findByUrl(anyString())).thenReturn(null);

        SiteResponse response = indexingService.removeSite("https://site.com");

        assertNotNull(response);
        assertTrue(response.isResult());
        assertNull(response.getError());
        verify(sitesList).removeSite("https://site.com");
    }

    @Test
    void testRemoveSite_WithTrailingSlash() {
        when(sitesList.removeSite(anyString())).thenReturn(true);
        when(siteRepository.findByUrl(anyString())).thenReturn(null);

        SiteResponse response = indexingService.removeSite("https://site.com/");

        assertNotNull(response);
        assertTrue(response.isResult());
        verify(sitesList).removeSite("https://site.com");
    }

    @Test
    void testRemoveSite_NotFound() {
        when(sitesList.removeSite(anyString())).thenReturn(false);

        SiteResponse response = indexingService.removeSite("https://nonexistent.com");

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Сайт с таким URL не найден в конфигурации", response.getError());
    }

    @Test
    void testRemoveSite_EmptyUrl() {
        SiteResponse response = indexingService.removeSite("");

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("URL не может быть пустым", response.getError());
        verify(sitesList, never()).removeSite(anyString());
    }

    @Test
    void testRemoveSite_NullUrl() {
        SiteResponse response = indexingService.removeSite(null);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("URL не может быть пустым", response.getError());
        verify(sitesList, never()).removeSite(anyString());
    }

    @Test
    void testRemoveSite_IndexingInProgress() {
        searchengine.model.Site siteEntity = new searchengine.model.Site();
        siteEntity.setUrl("https://site.com");
        siteEntity.setStatus(Status.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now());

        when(siteRepository.findByUrl(anyString())).thenReturn(siteEntity);

        SiteResponse response = indexingService.removeSite("https://site.com");

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Невозможно удалить сайт: индексация в процессе. Остановите индексацию перед удалением.", 
                     response.getError());
        verify(sitesList, never()).removeSite(anyString());
    }
}
