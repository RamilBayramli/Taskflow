package com.taskflow.repository;

import com.taskflow.entity.EmailCode;
import com.taskflow.entity.EmailCode.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {
    Optional<EmailCode> findByUserIdAndPurpose(Long userId, Purpose purpose);

    List<EmailCode> findByUserId(Long userId);
}
