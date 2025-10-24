package searchengine.dto.site;

import lombok.Data;

@Data
public class SiteResponse {
    private boolean result;
    private String error;

    public SiteResponse(boolean result) {
        this.result = result;
    }

    public SiteResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }
}
