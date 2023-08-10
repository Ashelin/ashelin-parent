package com.ashelin.orderservice.service;

import com.ashelin.orderservice.dto.InventoryResponse;
import com.ashelin.orderservice.dto.OrderLineItemsDto;
import com.ashelin.orderservice.dto.OrderRequest;
import com.ashelin.orderservice.model.Order;
import com.ashelin.orderservice.model.OrderLineItems;
import com.ashelin.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient webClient;

    public void placeOrder(OrderRequest orderRequest){
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .toList();

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream().map(OrderLineItems::getSkuCode)
                .toList();

        InventoryResponse[] inventoryResponseArray = webClient.get()
                .uri("http://localhost:8082/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();

        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            for (OrderLineItems orderLineItem : order.getOrderLineItemsList()) {
                if (orderLineItem.getQuantity() <= 0) {
                    throw new IllegalArgumentException("Product " + orderLineItem.getSkuCode() + " is not in stock.");
                }
            }
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Some products are not in stock, please try again later");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}