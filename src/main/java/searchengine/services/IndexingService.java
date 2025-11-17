package searchengine.services;

import org.springframework.stereotype.Service;

public interface IndexingService {

    boolean startIndexing();

    boolean stopIndexing();

    boolean indexPage(String pageLink);

    boolean isIndexing();
}
