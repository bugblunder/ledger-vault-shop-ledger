package com.khatavision.backend.repository;

import com.khatavision.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    @Query("select c.shop.id from Customer c where c.id = :id")
    Optional<Long> findShopIdById(@Param("id") Long id);

    Optional<Customer> findByContactInfo(String contactInfo);
    List<Customer> findByShopId(Long shopId);
}