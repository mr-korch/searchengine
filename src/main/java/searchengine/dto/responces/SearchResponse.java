package searchengine.dto.responces;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResponse {

    private boolean result;
    private Integer count;
    private List<SearchResult> data;
}
