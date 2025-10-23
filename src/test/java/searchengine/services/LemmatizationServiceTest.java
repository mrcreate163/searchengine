package searchengine.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LemmatizationServiceTest {

    private LemmatizationService lemmatizationService;

    @BeforeEach
    void setUp() throws IOException {
        lemmatizationService = new LemmatizationService();
    }

    @Test
    void testGetLemmas_RussianText() {
        String text = "Повторное появление леопарда в Осетии позволяет предположить";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertFalse(lemmas.isEmpty());
        assertTrue(lemmas.containsKey("леопард"));
        assertTrue(lemmas.containsKey("осетия"));
        assertTrue(lemmas.containsKey("появление"));
    }

    @Test
    void testGetLemmas_EnglishText() {
        String text = "The quick brown fox jumps over the lazy dog";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertFalse(lemmas.isEmpty());
        assertTrue(lemmas.containsKey("quick"));
        assertTrue(lemmas.containsKey("brown"));
        assertTrue(lemmas.containsKey("fox"));
    }

    @Test
    void testGetLemmas_MixedText() {
        String text = "Searching поиск information информация";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertFalse(lemmas.isEmpty());
    }

    @Test
    void testGetLemmas_EmptyText() {
        String text = "";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertTrue(lemmas.isEmpty());
    }

    @Test
    void testGetLemmas_CountsWords() {
        String text = "поиск информации поиск данных поиск";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertTrue(lemmas.containsKey("поиск"));
        assertEquals(3, lemmas.get("поиск"));
    }

    @Test
    void testGetLemmaSet_RussianText() {
        String text = "Повторное появление леопарда повторное";
        Set<String> lemmas = lemmatizationService.getLemmaSet(text);

        assertNotNull(lemmas);
        assertFalse(lemmas.isEmpty());
        assertTrue(lemmas.contains("повторный"));
        assertTrue(lemmas.contains("появление"));
        assertTrue(lemmas.contains("леопард"));
        assertEquals(3, lemmas.size()); // Should not count duplicates
    }

    @Test
    void testGetLemmaSet_EmptyText() {
        String text = "";
        Set<String> lemmas = lemmatizationService.getLemmaSet(text);

        assertNotNull(lemmas);
        assertTrue(lemmas.isEmpty());
    }

    @Test
    void testCleanHtmlContent() {
        String html = "<html><head><title>Test</title></head><body><h1>Header</h1><p>Paragraph text</p></body></html>";
        String cleaned = lemmatizationService.cleanHtmlContent(html);

        assertNotNull(cleaned);
        assertFalse(cleaned.contains("<"));
        assertFalse(cleaned.contains(">"));
        assertTrue(cleaned.contains("Test"));
        assertTrue(cleaned.contains("Header"));
        assertTrue(cleaned.contains("Paragraph text"));
    }

    @Test
    void testCleanHtmlContent_WithMultipleSpaces() {
        String html = "<div>Text   with     multiple    spaces</div>";
        String cleaned = lemmatizationService.cleanHtmlContent(html);

        assertNotNull(cleaned);
        assertFalse(cleaned.contains("  ")); // Should not contain double spaces
        assertTrue(cleaned.contains("Text with multiple spaces"));
    }

    @Test
    void testFilterParticles() {
        String text = "и или но у в на с";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        // Particles and prepositions should be filtered out
        assertNotNull(lemmas);
        // Most of these should be filtered
        assertTrue(lemmas.isEmpty() || lemmas.size() < 3);
    }

    @Test
    void testSpecialCharactersRemoval() {
        String text = "поиск!@#$%^&*()информации";
        Map<String, Integer> lemmas = lemmatizationService.getLemmas(text);

        assertNotNull(lemmas);
        assertTrue(lemmas.containsKey("поиск"));
        assertTrue(lemmas.containsKey("информация"));
    }

    @Test
    void testCaseInsensitivity() {
        String text1 = "ПОИСК";
        String text2 = "поиск";
        String text3 = "ПоИсК";

        Map<String, Integer> lemmas1 = lemmatizationService.getLemmas(text1);
        Map<String, Integer> lemmas2 = lemmatizationService.getLemmas(text2);
        Map<String, Integer> lemmas3 = lemmatizationService.getLemmas(text3);

        // All should produce the same lemma
        assertNotNull(lemmas1);
        assertNotNull(lemmas2);
        assertNotNull(lemmas3);
        assertFalse(lemmas1.isEmpty());
        assertEquals(lemmas1.keySet(), lemmas2.keySet());
        assertEquals(lemmas2.keySet(), lemmas3.keySet());
    }
}
