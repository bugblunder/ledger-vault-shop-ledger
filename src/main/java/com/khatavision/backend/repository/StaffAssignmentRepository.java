package com.khatavision.backend.repository;

import com.khatavision.backend.entity.StaffAssignment;
import com.khatavision.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {
    Optional<StaffAssignment> findByStaff(User staff);
    List<StaffAssignment> findByOwnerId(Long ownerId);
}