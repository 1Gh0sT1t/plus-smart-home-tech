package ru.yandex.practicum.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.inventory.dto.InventoryDto;
import ru.yandex.practicum.inventory.dto.ReserveRequest;
import ru.yandex.practicum.inventory.dto.ReserveResponse;
import ru.yandex.practicum.inventory.dto.UpdateInventoryRequest;
import ru.yandex.practicum.inventory.entity.Inventory;
import ru.yandex.practicum.inventory.exception.ConflictException;
import ru.yandex.practicum.inventory.exception.InsufficientStockException;
import ru.yandex.practicum.inventory.exception.NotFoundException;
import ru.yandex.practicum.inventory.mapper.InventoryMapper;
import ru.yandex.practicum.inventory.repository.InventoryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    @Override
    public List<InventoryDto> getAll() {
        return inventoryRepository.findAll().stream()
                .map(InventoryMapper::toDto)
                .toList();
    }

    @Override
    public InventoryDto getByProductId(Long productId) {
        return InventoryMapper.toDto(findByProductId(productId));
    }

    @Override
    @Transactional
    public InventoryDto create(UpdateInventoryRequest request) {
        if (inventoryRepository.existsByProductId(request.productId())) {
            throw new ConflictException(
                    "Складская запись для товара с id=" + request.productId() + " уже существует"
            );
        }
        Inventory inventory = new Inventory(request.productId(), request.quantity());
        return InventoryMapper.toDto(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional
    public InventoryDto update(UpdateInventoryRequest request) {
        Inventory inventory = findByProductId(request.productId());
        inventory.updateQuantity(request.quantity());
        return InventoryMapper.toDto(inventoryRepository.save(inventory));
    }

    @Override
    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        Inventory inventory = findByProductId(request.productId());
        if (request.quantity() > inventory.getAvailableQuantity()) {
            throw new InsufficientStockException(
                    "Недостаточно товара с id=" + request.productId()
                            + ". Доступно: " + inventory.getAvailableQuantity()
            );
        }

        inventory.reserve(request.quantity());
        inventoryRepository.save(inventory);
        return new ReserveResponse(
                true,
                inventory.getAvailableQuantity(),
                "Товар успешно зарезервирован"
        );
    }

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException(
                        "Складская запись для товара с id=" + productId + " не найдена"
                ));
    }
}
