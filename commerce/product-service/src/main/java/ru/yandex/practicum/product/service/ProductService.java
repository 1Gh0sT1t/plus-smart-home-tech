package ru.yandex.practicum.product.service;

import ru.yandex.practicum.product.dto.CreateProductRequest;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.dto.UpdateProductRequest;

import java.util.List;

public interface ProductService {

    List<ProductDto> getAll();

    ProductDto getById(Long id);

    List<ProductDto> getByCategory(Long categoryId);

    List<ProductDto> search(String query);

    ProductDto create(CreateProductRequest request);

    ProductDto update(Long id, UpdateProductRequest request);
}
