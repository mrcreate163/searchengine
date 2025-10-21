package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.SiteMapBuilder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final LemmatizationService lemmatizationService;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
        private volatile ForkJoinPool forkJoinPool;


    @Override
    public IndexingResponse startIndexing() {
        if (isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }

        siteRepository.deleteAll();
        SiteMapBuilder.resetIndexing();

        forkJoinPool = new ForkJoinPool();

        for (Site configSite : sitesList.getSites()) {
            searchengine.model.Site siteEntity = new searchengine.model.Site();
            siteEntity.setUrl(configSite.getUrl());
            siteEntity.setName(configSite.getName());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            SiteMapBuilder siteMapBuilder = new SiteMapBuilder(
                    configSite.getUrl(),
                    siteEntity,
                    siteRepository,
                    pageRepository,
                    lemmatizationService,
                    lemmaRepository,
                    indexRepository
            );

            forkJoinPool.execute(siteMapBuilder);
        }

        new Thread(this::monitorIndexing).start();

        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация не запущена");
        }

        SiteMapBuilder.stopIndexing();
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow();
        }

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(Status.INDEXING)) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });

        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse indexPage(String url) {
                // Проверяем принадлежит ли URL к одному из сайтов в конфигурации
        Site configSite = null;
        String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        
        for (Site site : sitesList.getSites()) {
            String normalizedSiteUrl = site.getUrl().endsWith("/") ? site.getUrl().substring(0, site.getUrl().length() - 1) : site.getUrl();
            if (normalizedUrl.startsWith(normalizedSiteUrl)) {
                configSite = site;
                break;
            }
        }

        if (configSite == null) {
            return new IndexingResponse(false, "Данный URL находится за пределами сайтов, указанных в конфигурации");
        }

        try {
            //Получаем или создаём сайт в БД
            searchengine.model.Site siteEntity = siteRepository.findByUrl(configSite.getUrl());
            if (siteEntity == null) {
                siteEntity = new searchengine.model.Site();
                siteEntity.setUrl(configSite.getUrl());
                siteEntity.setName(configSite.getName());
                siteEntity.setStatus(Status.INDEXED);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }

            String normalizedSiteUrl = configSite.getUrl().endsWith("/") ? configSite.getUrl().substring(0, configSite.getUrl().length() - 1) : configSite.getUrl();
            String path = normalizedUrl.replace(normalizedSiteUrl, "");
            if (path.isEmpty()) {
                path = "/";
            }

            Page existingPage = pageRepository.findBySiteAndPath(siteEntity, path);
            if (existingPage != null) {
                indexRepository.deleteByPage(existingPage);
                pageRepository.delete(existingPage);
            }

            //загружаем и сохраняем страницу
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .timeout(10000)
                    .execute();

            Document document = response.parse();
            String content = document.html();

            Page page = new Page();
            page.setSite(siteEntity);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(content);
            pageRepository.save(page);


            if (response.statusCode() == 200) {
                indexPageContent(page, content, siteEntity);
            }

            return new IndexingResponse(true);
        } catch (Exception e) {
            return new IndexingResponse(false, "Ошибка при индексации страницы: " + e.getMessage());
        }
    }

    private void indexPageContent(Page page, String content, searchengine.model.Site configSite) {
        try {
            String cleanContent = lemmatizationService.cleanHtmlContent(content);
            Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanContent);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaWord = entry.getKey();
                Integer count = entry.getValue();

                //Ищем или создаём лемму
                Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(configSite, lemmaWord);
                Lemma lemma;

                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                    lemma.setFrequency(lemma.getFrequency() + 1);
                } else {
                    lemma = new Lemma();
                    lemma.setSite(configSite);
                    lemma.setLemma(lemmaWord);
                    lemma.setFrequency(1);
                }
                lemmaRepository.save(lemma);

                Index index = new Index();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(count.floatValue());
                indexRepository.save(index);
            }
                } catch (Exception e) {
            log.error("Ошибка при индексации содержимого страницы: " + page.getPath(), e);
        }
    }

    private boolean isIndexingRunning() {
        return siteRepository.findAll().stream()
                .anyMatch(site -> site.getStatus().equals(Status.INDEXING));
    }

    private void monitorIndexing() {
        try {
            if (forkJoinPool != null) {
                                ForkJoinPool pool = forkJoinPool;
                while (!pool.isTerminated()) {
                    Thread.sleep(1000);
                }

                siteRepository.findAll().forEach(site -> {
                    if (site.getStatus().equals(Status.INDEXING)) {
                        site.setStatus(Status.INDEXED);
                        site.setStatusTime(LocalDateTime.now());
                        siteRepository.save(site);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
