package searchengine.services;

import searchengine.dto.responces.SearchResponse;
import searchengine.dto.responces.SearchResult;

public interface SearchService {

    SearchResponse search(String query, String site);

}
