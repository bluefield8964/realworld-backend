package realworld_backend.common.dto.responseBody;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAccessResponse {
    private String viewerState;
    private boolean canReadBody;
    private boolean subscriptionRequired;
    private String bundleCode;
}
