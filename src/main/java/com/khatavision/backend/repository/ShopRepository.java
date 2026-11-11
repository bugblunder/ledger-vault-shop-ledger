package com.khatavision.backend.repository;
import com.khatavision.backend.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Shop s where s.id = :id")
    java.util.Optional<Shop> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select s from Shop s where s.id = :id")
    java.util.Optional<Shop> findByIdForRead(@org.springframework.data.repository.query.Param("id") Long id);

    List<Shop> findByOwnerId(Long ownerId);
}