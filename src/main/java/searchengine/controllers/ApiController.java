package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.responces.Response;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;

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

}
