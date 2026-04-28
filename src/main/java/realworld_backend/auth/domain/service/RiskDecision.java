package realworld_backend.auth.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiskDecision {
    private final String level;
    private final boolean requireMfa;
    public static RiskDecision allow() {
        return new RiskDecision("LOW", false);
    }
}
