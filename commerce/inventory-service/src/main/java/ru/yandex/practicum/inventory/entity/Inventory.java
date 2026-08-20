package ru.yandex.practicum.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int reservedQuantity;

    @Version
    private Long version;

    public Inventory(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    public void updateQuantity(int newQuantity) {
        if (newQuantity < reservedQuantity) {
            throw new IllegalArgumentException(
                    "Количество товара не может быть меньше уже зарезервированного количества"
            );
        }
        quantity = newQuantity;
    }

    public void reserve(int requestedQuantity) {
        if (requestedQuantity > getAvailableQuantity()) {
            throw new IllegalArgumentException("Недостаточно доступного товара");
        }
        reservedQuantity += requestedQuantity;
    }
}
