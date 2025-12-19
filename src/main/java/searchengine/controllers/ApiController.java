package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.responces.Response;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SiteRepository siteRepository;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<?> startIndexing() {

        boolean isStarted = indexingService.startIndexing();

        if (isStarted) {
            return ResponseEntity.ok(new Response((true)));
        } else {
            return ResponseEntity.badRequest().body(new Response(false, "Индексация уже запущена"));
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<?> stopIndexing() {

        boolean isStopped = indexingService.stopIndexing();

        if (isStopped) {
            return ResponseEntity.ok(new Response((true)));
        } else {
            return ResponseEntity.badRequest().body(new Response(false, "Индексация не запущена"));
        }
    }

    @GetMapping("/indexPage")
    public ResponseEntity<?> indexPage(@RequestParam String pageLink) {

        boolean isSucceeded = indexingService.indexPage(pageLink);

        if (isSucceeded) {
            return ResponseEntity.ok(new Response((true)));
        } else {
            return ResponseEntity.badRequest().body(new Response(false, "Данная страница находится за пределами сайтов, \n" +
                    "указанных в конфигурационном файле"));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchByQuery(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "20") Integer limit) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(new Response(false, "Задан пустой поисковый запрос"));
        }
        if (site != null && !indexingService.isSiteIndexed(site)) {
            return ResponseEntity.badRequest().body(new Response(false, "Указанная страница не найдена"));
        }
        return ResponseEntity.ok(searchService.search(query, site, offset, limit));
    }

}
