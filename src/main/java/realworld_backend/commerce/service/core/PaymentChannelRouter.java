package realworld_backend.commerce.service.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentChannelRouter {
    private final List<PaymentChannel> channels;
    private Map<String, PaymentChannel> byProvider;

    @PostConstruct
    void init() {
        byProvider = channels.stream()
                .collect(Collectors.toMap(PaymentChannel::provider, c -> c));
    }

    public PaymentChannel get(String provider) {
        PaymentChannel channel = byProvider.get(provider);
        if (channel == null) throw new IllegalArgumentException("unsupported provider: " + provider);
        return channel;
    }
}

