package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmatizationService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteMapBuilder extends RecursiveAction {

    private String url;
    private Site site;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmatizationService lemmatizationService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static final Set<String> allLinks = new CopyOnWriteArraySet<>();
    private static volatile boolean isIndexingStopped = false;



    public SiteMapBuilder(String url, Site site,
                          SiteRepository siteRepository,
                          PageRepository pageRepository,
                          LemmatizationService lemmatizationService,
                          LemmaRepository lemmaRepository,
                          IndexRepository indexRepository) {
        this.url = url;
        this.site = site;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmatizationService = lemmatizationService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }


    @Override
    protected void compute() {
        if (isIndexingStopped) {
            return;
        }
        try {
            Thread.sleep(500);

            String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            String normalizedSiteUrl = site.getUrl().endsWith("/") ? site.getUrl().substring(0, site.getUrl().length() - 1) : site.getUrl();
            String path = normalizedUrl.replace(normalizedSiteUrl, "");
            if (path.isEmpty()) {
                path = "/";
            }

            // Проверяем, не обрабатывали ли мы уже эту страницу
            if (allLinks.contains(url)) {
                return;
            }

            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .timeout(10000)
                    .execute();

            Document document = response.parse();
            String content = document.html();

            // Сохраняем страницу
            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(content);

            synchronized (this) {
                pageRepository.save(page);


                // Обновляем время последней активности
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }

            // Ищем ссылки только на успешных страницах
            if (response.statusCode() == 200) {
                indexPageContent(page, content);

                Set<SiteMapBuilder> taskList = new HashSet<>();
                Elements links = document.select("a[href]");

                for (Element link : links) {
                    String childUrl = link.absUrl("href");

                    if (isValidUrl(childUrl, site.getUrl()) && !allLinks.contains(childUrl)) {
                        SiteMapBuilder task = new SiteMapBuilder(
                                childUrl,
                                site,
                                siteRepository,
                                pageRepository,
                                lemmatizationService,
                                lemmaRepository,
                                indexRepository
                        );
                        task.fork();
                        taskList.add(task);
                    }
                }

                for (SiteMapBuilder task : taskList) {
                    task.join();
                }
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    private void indexPageContent(Page page, String content) {
        try {
            String cleanContent = lemmatizationService.cleanHtmlContent(content);
            Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanContent);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaWord = entry.getKey();
                Integer count = entry.getValue();

                synchronized (this) {
                    // Ищем или создаем лемму
                    Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(site, lemmaWord);
                    Lemma lemma;

                    if (optionalLemma.isPresent()) {
                        lemma = optionalLemma.get();
                        lemma.setFrequency(lemma.getFrequency() + 1);
                    } else {
                        lemma = new Lemma();
                        lemma.setSite(site);
                        lemma.setLemma(lemmaWord);
                        lemma.setFrequency(1);
                    }
                    lemmaRepository.save(lemma);

                    // Создаем индекс
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(count.floatValue());
                    indexRepository.save(index);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при индексации содержимого страницы: " + page.getPath(), e);
        }
    }

    private boolean isValidUrl(String url, String siteUrl) {
        return url.startsWith(siteUrl) &&
                !url.contains("#") &&
                !url.matches(".*(\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|mp3|mp4|avi|mov|wmv|zip|rar))$") &&
                url.length() < 255;
    }

    private void handleError(Exception e) {
        synchronized (this) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getMessage());
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    public static void stopIndexing() {
        isIndexingStopped = true;
    }

    public static void resetIndexing() {
        isIndexingStopped = false;
        allLinks.clear();
    }
}
