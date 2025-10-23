package searchengine.dto.search;

import lombok.Data;

import java.util.List;

@Data
public class SearchResponse {
    private boolean result;
    private String error;
    private int count;
    private List<SearchData> data;

    public SearchResponse(boolean result) {
        this.result = result;
    }

    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    public SearchResponse(boolean result, int count, List<SearchData> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }

}
