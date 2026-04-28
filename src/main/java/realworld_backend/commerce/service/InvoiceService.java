package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.PaymentStatus;
import realworld_backend.commerce.model.core.ProviderInvoice;
import realworld_backend.commerce.model.invoice.Invoice;
import realworld_backend.commerce.model.invoice.InvoiceWebhookEvent;
import realworld_backend.commerce.repository.InvoiceRepository;
import realworld_backend.commerce.service.core.ProviderTimeMapper;

import java.time.Instant;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    public void upsertInvoiceByEvent(InvoiceWebhookEvent event, String subscriptionNo, PaymentStatus accountingStatus) {
        InvoiceWebhookEvent.InvoiceObject invoiceObject = event.getObject();
        Invoice record = invoiceRepository.findByInvoiceId(invoiceObject.getId())
                .orElseGet(Invoice::new);

        record.setProvider(event.getProvider());
        record.setEventId(event.getEventId());
        record.setEventType(event.getEventType());
        record.setInvoiceId(invoiceObject.getId());
        record.setSubscriptionNo(subscriptionNo);
        record.setProviderSubscriptionId(invoiceObject.getSubscription());
        record.setProviderCustomerId(invoiceObject.getCustomer());
        record.setPaymentIntentId(invoiceObject.getPaymentIntent());
        record.setAmountDue(invoiceObject.getAmountDue());
        record.setAmountPaid(invoiceObject.getAmountPaid());
        record.setAmountRemaining(invoiceObject.getAmountRemaining());
        record.setCurrency(invoiceObject.getCurrency());
        record.setProviderInvoiceStatus(invoiceObject.getStatus());
        record.setPaymentStatus(accountingStatus);
        record.setPaid(invoiceObject.getPaid());
        record.setAttemptCount(invoiceObject.getAttemptCount());
        record.setPeriodStart(ProviderTimeMapper.toUtcLocalDateTime(invoiceObject.periodStartInstant()));
        record.setPeriodEnd(ProviderTimeMapper.toUtcLocalDateTime(invoiceObject.periodEndInstant()));
        record.setNextPaymentAttemptAt(ProviderTimeMapper.toUtcLocalDateTime(invoiceObject.nextPaymentAttemptInstant()));
        record.setProviderCreatedAt(ProviderTimeMapper.toUtcLocalDateTime(invoiceObject.createdAtInstant()));

        if (invoiceObject.getLastPaymentError() != null) {
            record.setFailureCode(invoiceObject.getLastPaymentError().getCode());
            record.setFailureMessage(invoiceObject.getLastPaymentError().getMessage());
        } else {
            record.setFailureCode(null);
            record.setFailureMessage(null);
        }

        LocalDateTime now = ProviderTimeMapper.toUtcLocalDateTime(Instant.now());
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
        invoiceRepository.save(record);
    }

    public void upsertInvoiceByRetrieve(String provider, ProviderInvoice providerInvoice, String subscriptionNo) {
        Invoice record = invoiceRepository.findByInvoiceId(providerInvoice.getId())
                .orElseGet(Invoice::new);

        record.setProvider(provider);
        record.setInvoiceId(providerInvoice.getId());
        record.setSubscriptionNo(subscriptionNo);
        record.setProviderSubscriptionId(providerInvoice.getSubscription());
        record.setProviderCustomerId(providerInvoice.getCustomer());
        record.setPaymentIntentId(providerInvoice.getPaymentIntent());
        record.setAmountDue(providerInvoice.getAmountDue());
        record.setAmountPaid(providerInvoice.getAmountPaid());
        record.setAmountRemaining(providerInvoice.getAmountRemaining());
        record.setCurrency(providerInvoice.getCurrency());
        record.setProviderInvoiceStatus(providerInvoice.getStatus());
        record.setPaymentStatus(resolveAccountingStatus(providerInvoice));
        record.setPaid(providerInvoice.getPaid());
        record.setAttemptCount(providerInvoice.getAttemptCount() == null ? null : providerInvoice.getAttemptCount().intValue());
        record.setPeriodStart(providerInvoice.periodStartUtc());
        record.setPeriodEnd(providerInvoice.periodEndUtc());
        record.setNextPaymentAttemptAt(providerInvoice.nextPaymentAttemptUtc());
        record.setProviderCreatedAt(providerInvoice.createdAtUtc());

        LocalDateTime now = ProviderTimeMapper.toUtcLocalDateTime(Instant.now());
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
        invoiceRepository.save(record);
    }

    private PaymentStatus resolveAccountingStatus(ProviderInvoice providerInvoice) {
        if (providerInvoice == null) {
            return PaymentStatus.FAILED;
        }
        if (providerInvoice.isPaidSuccessfully()) {
            return PaymentStatus.SUCCESS;
        }
        String providerStatus = providerInvoice.getStatus();
        if (providerStatus == null || providerStatus.isBlank()) {
            return PaymentStatus.PROCESSING;
        }
        return switch (providerStatus) {
            case "open", "draft" -> PaymentStatus.PROCESSING;
            case "void", "uncollectible" -> PaymentStatus.FAILED;
            default -> PaymentStatus.FAILED;
        };
    }
}
