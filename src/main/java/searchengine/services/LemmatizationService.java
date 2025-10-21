package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.english.EnglishMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianMorphology;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class LemmatizationService {

    private final LuceneMorphology russianMorphology;
    private final LuceneMorphology englishMorphology;
    private static final String[] PARTICLES_NAMES = {"ПРЕДЛ", "СОЮЗ", "МЕЖД"};

    public LemmatizationService() throws IOException {
        this.russianMorphology = new RussianLuceneMorphology();
        this.englishMorphology = new EnglishLuceneMorphology();
    }

    public Map<String, Integer> getLemmas(String text) {
        String[] words = arrayContainsRussianWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) continue;

            List<String> wordBaseForms = getMorphInfo(word);
            if (wordBaseForms.isEmpty()) continue;

            List<String> normalForms = getNormalForms(word);
            if (normalForms.isEmpty()) continue;

            String normalWord = normalForms.get(0);

            if (filterLemma(wordBaseForms)) {
                lemmas.put(normalWord, lemmas.getOrDefault(normalWord, 0) + 1);
            }
        }

        return lemmas;
    }

    public Set<String> getLemmaSet(String text) {
        String[] words = arrayContainsRussianWords(text);
        Set<String> lemmas = new HashSet<>();

        for (String word : words) {
            if (word.isBlank()) continue;

            List<String> wordBaseForms = getMorphInfo(word);
            if (wordBaseForms.isEmpty()) continue;

            List<String> normalForms = getNormalForms(word);
            if (normalForms.isEmpty()) continue;

            String normalWord = normalForms.get(0);

            if (filterLemma(wordBaseForms)) {
                lemmas.add(normalWord);
            }
        }
        return lemmas;
    }

    private List<String> getMorphInfo(String word) {
        List<String> morphInfo = new ArrayList<>();
        try {
            if (word.matches("[а-яё]+")) {
                morphInfo = russianMorphology.getMorphInfo(word);
            } else if (word.matches("[a-z]+")) {
                morphInfo = englishMorphology.getMorphInfo(word);
            }
        } catch (Exception e) {
            log.error("Ошибка при получении морфологической информации для слова: " + word, e);
        }
        return morphInfo;
    }

    private List<String> getNormalForms(String word) {
        List<String> normalForms = new ArrayList<>();
        try {
            if (word.matches("[а-яё]+")) {
                normalForms = russianMorphology.getNormalForms(word);
            } else if (word.matches("[a-z]+")) {
                normalForms = englishMorphology.getNormalForms(word);
            }
        } catch (Exception e) {
            log.error("Ошибка при получении нормальных форм для слова: " + word, e);
        }
        return normalForms;
    }

    private boolean filterLemma(List<String> wordBaseForms) {
        return wordBaseForms.stream()
                .noneMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String word) {
        for (String property : PARTICLES_NAMES) {
            if (word.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^а-яёa-z\\s]", " ")
                .trim()
                .split("\\s+");
    }

    public String cleanHtmlContent(String content) {
        return  content.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }


}
