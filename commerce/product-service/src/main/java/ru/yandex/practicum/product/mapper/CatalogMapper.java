package ru.yandex.practicum.product.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ru.yandex.practicum.product.dto.CategoryDto;
import ru.yandex.practicum.product.dto.ProductDto;
import ru.yandex.practicum.product.entity.Category;
import ru.yandex.practicum.product.entity.Product;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CatalogMapper {

    public static CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getDescription());
    }

    public static ProductDto toDto(Product product) {
        CategoryDto category = product.getCategory() == null ? null : toDto(product.getCategory());
        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                category,
                product.getImageUrl(),
                product.isActive()
        );
    }
}
