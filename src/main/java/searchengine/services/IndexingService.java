package searchengine.services;

public interface IndexingService {

    boolean startIndexing();

    boolean stopIndexing();

    boolean indexPage(String pageLink);

    boolean isIndexing();

    boolean isSiteIndexed(String url);
}
