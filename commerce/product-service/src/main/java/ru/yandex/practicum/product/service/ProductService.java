package ru.yandex.practicum.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.product.dto.CreateProductRequest;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.dto.UpdateProductRequest;
import ru.yandex.practicum.product.entity.Category;
import ru.yandex.practicum.product.entity.Product;
import ru.yandex.practicum.product.exception.NotFoundException;
import ru.yandex.practicum.product.mapper.CatalogMapper;
import ru.yandex.practicum.product.repository.CategoryRepository;
import ru.yandex.practicum.product.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public List<ProductDto> getAll() {
        return productRepository.findAllByActiveTrue().stream()
                .map(CatalogMapper::toDto)
                .toList();
    }

    public ProductDto getById(Long id) {
        return CatalogMapper.toDto(findById(id));
    }

    public List<ProductDto> getByCategory(Long categoryId) {
        findCategoryById(categoryId);
        return productRepository.findAllByCategoryIdAndActiveTrue(categoryId).stream()
                .map(CatalogMapper::toDto)
                .toList();
    }

    public List<ProductDto> search(String query) {
        return productRepository.findAllByActiveTrueAndNameContainingIgnoreCase(query).stream()
                .map(CatalogMapper::toDto)
                .toList();
    }

    @Transactional
    public ProductDto create(CreateProductRequest request) {
        Category category = request.categoryId() == null ? null : findCategoryById(request.categoryId());
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                category,
                request.imageUrl()
        );
        return CatalogMapper.toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, UpdateProductRequest request) {
        Product product = findById(id);

        if (request.name() != null) {
            product.setName(request.name());
        }
        if (request.description() != null) {
            product.setDescription(request.description());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.categoryId() != null) {
            product.setCategory(findCategoryById(request.categoryId()));
        }
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.active() != null) {
            product.setActive(request.active());
        }

        return CatalogMapper.toDto(productRepository.save(product));
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));
    }

    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + id + " не найдена"));
    }
}
