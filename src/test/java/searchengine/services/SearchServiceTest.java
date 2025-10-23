package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private LemmatizationService lemmatizationService;

    @Mock
    private LemmaRepository lemmaRepository;

    @Mock
    private IndexRepository indexRepository;

    @Mock
    private PageRepository pageRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private SitesList sitesList;

    @InjectMocks
    private SearchServiceImpl searchService;

    private searchengine.model.Site siteEntity;
    private Page page1;
    private Page page2;
    private Lemma lemma1;
    private Lemma lemma2;
    private Index index1;
    private Index index2;

    @BeforeEach
    void setUp() {
        // Setup site
        siteEntity = new searchengine.model.Site();
        siteEntity.setId(1);
        siteEntity.setUrl("https://test.com");
        siteEntity.setName("Test Site");
        siteEntity.setStatus(Status.INDEXED);
        siteEntity.setStatusTime(LocalDateTime.now());

        // Setup pages
        page1 = new Page();
        page1.setId(1);
        page1.setSite(siteEntity);
        page1.setPath("/page1");
        page1.setCode(200);
        page1.setContent("<html><head><title>Page 1</title></head><body>Content with search word</body></html>");

        page2 = new Page();
        page2.setId(2);
        page2.setSite(siteEntity);
        page2.setPath("/page2");
        page2.setCode(200);
        page2.setContent("<html><head><title>Page 2</title></head><body>Another page content</body></html>");

        // Setup lemmas
        lemma1 = new Lemma();
        lemma1.setId(1);
        lemma1.setSite(siteEntity);
        lemma1.setLemma("поиск");
        lemma1.setFrequency(2);

        lemma2 = new Lemma();
        lemma2.setId(2);
        lemma2.setSite(siteEntity);
        lemma2.setLemma("информация");
        lemma2.setFrequency(1);

        // Setup indexes
        index1 = new Index();
        index1.setId(1);
        index1.setPage(page1);
        index1.setLemma(lemma1);
        index1.setRank(5.0f);

        index2 = new Index();
        index2.setId(2);
        index2.setPage(page1);
        index2.setLemma(lemma2);
        index2.setRank(3.0f);
    }

    @Test
    void testSearch_EmptyQuery() {
        SearchResponse response = searchService.search("", null, 0, 20);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Задан пустой поисковый запрос", response.getError());
    }

    @Test
    void testSearch_NullQuery() {
        SearchResponse response = searchService.search(null, null, 0, 20);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Задан пустой поисковый запрос", response.getError());
    }

    @Test
    void testSearch_NoLemmasFound() {
        when(lemmatizationService.getLemmaSet(anyString())).thenReturn(Collections.emptySet());

        SearchResponse response = searchService.search("test query", null, 0, 20);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertTrue(response.getError().contains("ничего не найдено"));
    }

    @Test
    void testSearch_SiteNotFound() {
        Set<String> lemmas = new HashSet<>(Arrays.asList("поиск"));
        when(lemmatizationService.getLemmaSet(anyString())).thenReturn(lemmas);
        when(siteRepository.findByUrl("https://nonexistent.com")).thenReturn(null);

        SearchResponse response = searchService.search("поиск", "https://nonexistent.com", 0, 20);

        assertNotNull(response);
        assertFalse(response.isResult());
        assertEquals("Сайт для поиска не найден", response.getError());
    }

    // These tests require integration testing with actual database
    // Mocking the complex behavior of SearchService with streams and immutable collections
    // is fragile and doesn't add much value over integration tests

    @Test
    void testSearch_NoResultsFound() {
        Set<String> lemmas = new HashSet<>(Arrays.asList("поиск"));
        when(lemmatizationService.getLemmaSet(anyString())).thenReturn(lemmas);
        when(siteRepository.findAll()).thenReturn(Collections.singletonList(siteEntity));
        when(lemmaRepository.findLemmasBySiteAndLemmaIn(any(), anyList()))
                .thenReturn(Collections.emptyList()); // No lemmas found in DB

        SearchResponse response = searchService.search("поиск", null, 0, 20);

        assertNotNull(response);
        assertTrue(response.isResult());
        assertEquals(0, response.getCount());
        assertTrue(response.getData().isEmpty());
    }

    @Test
    void testSearch_FrequentLemmasFiltered() {
        lemma1.setFrequency(90); // Very frequent lemma (90 out of 100)
        Set<String> lemmas = new HashSet<>(Arrays.asList("поиск"));
        when(lemmatizationService.getLemmaSet(anyString())).thenReturn(lemmas);
        when(siteRepository.findAll()).thenReturn(Collections.singletonList(siteEntity));
        when(lemmaRepository.findLemmasBySiteAndLemmaIn(any(), anyList()))
                .thenReturn(Collections.singletonList(lemma1));
        when(lemmaRepository.countTotalLemmasBySite(siteEntity)).thenReturn(100L);

        SearchResponse response = searchService.search("поиск", null, 0, 20);

        assertNotNull(response);
        assertTrue(response.isResult());
        assertEquals(0, response.getCount()); // Should be filtered due to high frequency
    }
}
