package com.taskflow.repository;

import com.taskflow.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    @EntityGraph(attributePaths = "category")
    List<Task> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "category")
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    List<Task> findByCategoryIdAndUserId(Long categoryId, Long userId);
}
