package realworld_backend.service;

import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import realworld_backend.repository.OrderRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
public class WebhookService {


    public final OrderRepository orderRepository;

    public WebhookService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }


    public void handleStripeEvent(String payload, String sigHeader, String secret) throws Exception {

        Event event = Webhook.constructEvent(payload, sigHeader, secret);
        String type = event.getType();
        log.info("EVENT: {}", type);
        log.info("RAW: {}", event.getDataObjectDeserializer().getRawJson());

        String rawJson = event.getDataObjectDeserializer().getRawJson();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(rawJson);
        JsonNode metadata = jsonNode.get("metadata");
        JsonNode Id = jsonNode.get("Id");
        switch (type) {
            case "checkout.session.completed":

                log.info("customer submit a payment process: {}", Id);

                break;
            case "payment_intent.created":
                log.info("PaymentIntent built: {}", type);
                break;
            case "payment_intent.succeeded":

                String orderNo = metadata.get("orderNo").asString();
                handlePaymentSuccess(orderNo);

                log.info("customer finish and paid the payment: {}", Id);
                break;
        }
    }
/*{
  "id": "pi_3TLNbYFBxenD80aO01qPh0Aj",
  "object": "payment_intent",
  "status": "succeeded",
  "amount": 1000,
  "amount_received": 1000,
  "amount_capturable": 0,
  "currency": "usd",
  "livemode": false,
  "created": 1775998752,
  "capture_method": "automatic",
  "confirmation_method": "automatic",
  "client_secret": "pi_3TLNbYFBxenD80aO01qPh0Aj_secret_95jS8gxNDCvHpV97ksSWaUh4Y",
  "latest_charge": "py_3TLNbYFBxenD80aO0FXqcG1t",
  "metadata": { "orderNo": "6fa34224-3f7c-4a58-928c-a1e668cf0f58"},
  "payment_method": "pm_1TLNbRFBxenD80aO7ISNwrQO",
  "payment_method_types": ["link"],
  "payment_method_options": {    "link": {"persistent_token": null  }  },
  "payment_details": {    "order_reference": "cs_test_a1QoSBepqHJviDsClTrzhEQ40TGVn1D90VpVWlYRSIXgixxBeceYU2I1ab",   "customer_reference": null},
  "amount_details": {
    "shipping": {     "amount": 0,      "from_postal_code": null,      "to_postal_code": null    },
    "tax": {      "total_tax_amount": 0    },
    "tip": {}
  }
}*/


    // critical module
    @Transactional
    public void handlePaymentSuccess(String orderNo) {

        orderRepository.findByOrderNo(orderNo).ifPresent(order -> {

            // ✅ 幂等处理（非常重要）
            if ("PAID".equals(order.getStatus())) {
                return;
            }

            order.setStatus("PAID");
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);
            log.info("database order was changed ,status as PAID ");
        });
    }

}
