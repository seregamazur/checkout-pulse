package com.seregamazur.pulse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.seregamazur.pulse.cart.Cart;
import com.seregamazur.pulse.cart.CartRepository;
import com.seregamazur.pulse.cart.CartService;
import com.seregamazur.pulse.infra.RedisStockProvider;
import com.seregamazur.pulse.infra.inbox.RedisStockInboxRepository;
import com.seregamazur.pulse.inventory.InventoryItem;
import com.seregamazur.pulse.inventory.InventoryRepository;
import com.seregamazur.pulse.inventory.InventoryService;
import com.seregamazur.pulse.inventory.Product;
import com.seregamazur.pulse.inventory.ProductRepository;
import com.seregamazur.pulse.inventory.inbox.InventoryInboxRepository;
import com.seregamazur.pulse.order.OrderQueueListener;
import com.seregamazur.pulse.order.OrderRepository;
import com.seregamazur.pulse.order.OrderService;
import com.seregamazur.pulse.order.idempotency.IdempotencyRepository;
import com.seregamazur.pulse.order.inbox.OrderInboxRepository;
import com.seregamazur.pulse.order.views.OrderProjectionHandler;
import com.seregamazur.pulse.order.views.OrderViewRepository;
import com.seregamazur.pulse.order.views.inbox.OrderViewInboxRepository;
import com.seregamazur.pulse.payment.PaymentRepository;
import com.seregamazur.pulse.payment.PaymentService;
import com.seregamazur.pulse.payment.inbox.PaymentInboxRepository;
import com.seregamazur.pulse.shared.event.OrderCreatedEvent;
import com.seregamazur.pulse.shared.outbox.OutboxRepository;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.docker.compose.enabled=false",
        "spring.flyway.enabled=false"
    }
)
@Import(TestcontainersConfiguration.class)
abstract class AbstractIntegrationTest {

    @Autowired
    protected CartService cartService;
    @Autowired
    protected CartRepository cartRepository;
    @Autowired
    protected OrderService orderService;
    @Autowired
    protected OrderRepository orderRepository;
    @Autowired
    protected InventoryService inventoryService;
    @Autowired
    protected InventoryRepository inventoryRepository;
    @Autowired
    protected PaymentService paymentService;
    @Autowired
    protected PaymentRepository paymentRepository;
    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected RedisStockProvider redisStockProvider;
    @Autowired
    protected OutboxRepository outboxRepository;
    @Autowired
    protected IdempotencyRepository idempotencyRepository;
    @Autowired
    protected OrderInboxRepository orderInboxRepository;
    @Autowired
    protected PaymentInboxRepository paymentInboxRepository;
    @Autowired
    protected InventoryInboxRepository inventoryInboxRepository;
    @Autowired
    protected RedisStockInboxRepository redisStockInboxRepository;
    @Autowired
    protected OrderViewRepository orderViewRepository;
    @Autowired
    protected OrderViewInboxRepository orderViewInboxRepository;
    @Autowired
    protected OrderProjectionHandler orderProjectionHandler;
    @Autowired
    protected OrderQueueListener orderQueueListener;

    @Autowired
    @Qualifier("redisCart")
    protected RedisTemplate<String, Object> redisCart;

    @Autowired
    @Qualifier("redisStock")
    protected RedisTemplate<String, Long> redisStock;

    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected PlatformTransactionManager txManager;

    protected static final UUID USER_ID = UUID.fromString("aaaa0000-0000-0000-0000-000000000001");

    @BeforeEach
    void cleanUp() {
        orderViewInboxRepository.deleteAll();
        orderInboxRepository.deleteAll();
        paymentInboxRepository.deleteAll();
        inventoryInboxRepository.deleteAll();
        redisStockInboxRepository.deleteAll();
        orderViewRepository.deleteAll();
        outboxRepository.deleteAll();
        idempotencyRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();

        redisStock.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    protected Product createProduct(String name, BigDecimal price, long stock) {
        Product product = new Product(name, price);
        product = productRepository.save(product);
        inventoryRepository.save(new InventoryItem(product.getId(), stock));
        return product;
    }

    protected Cart createCartWithItem(UUID userId, UUID productId, long quantity) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> new Cart(userId));
        cart.updateOrAddItem(productId, quantity);
        return cartRepository.save(cart);
    }

    protected List<OrderCreatedEvent.Item> eventItems(UUID productId, long quantity) {
        return List.of(new OrderCreatedEvent.Item(productId, quantity));
    }

    protected <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(txManager).execute(status -> work.get());
    }
}
