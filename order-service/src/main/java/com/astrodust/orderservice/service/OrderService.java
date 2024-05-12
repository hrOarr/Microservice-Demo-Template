package com.astrodust.orderservice.service;

import com.astrodust.orderservice.dto.InventoryResponse;
import com.astrodust.orderservice.dto.OrderItemsDto;
import com.astrodust.orderservice.dto.OrderRequest;
import com.astrodust.orderservice.entity.Order;
import com.astrodust.orderservice.entity.OrderItems;
import com.astrodust.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderItems> orderLineItems = orderRequest.getOrderItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderItemsList().stream()
                .map(OrderItems::getSkuCode)
                .toList();

        InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                .uri("http://inventory-service/api/v1/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
            return "Order has been placed successfully";
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    private OrderItems mapToDto(OrderItemsDto orderLineItemsDto) {
        OrderItems orderItems = new OrderItems();
        orderItems.setPrice(orderLineItemsDto.getPrice());
        orderItems.setQuantity(orderLineItemsDto.getQuantity());
        orderItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderItems;
    }
}
