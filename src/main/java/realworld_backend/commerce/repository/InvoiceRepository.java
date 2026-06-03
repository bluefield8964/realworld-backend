package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.invoice.Invoice;

import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByInvoiceId(String invoiceId);

    boolean existsBySubscriptionNo(String subscriptionNo);

    Optional<Invoice> findTopBySubscriptionNoOrderByProviderCreatedAtDescUpdatedAtDesc(String subscriptionNo);

    Optional<Invoice> findTopBySubscriptionNoAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualOrderByProviderCreatedAtDescUpdatedAtDesc(
            String subscriptionNo,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    );
}
