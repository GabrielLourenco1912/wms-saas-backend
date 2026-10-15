package dev.lourencogabriel.wmssaas.task.repository;

import dev.lourencogabriel.wmssaas.task.model.TaskHistory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface TaskHistoryRepository extends Repository<TaskHistory, String> {

    TaskHistory save(TaskHistory entry);
    Optional<TaskHistory> findById(String id);
    List<TaskHistory> findAll();
    Page<TaskHistory> findAll(Pageable pageable);
    Page<TaskHistory> findAll(Specification<TaskHistory> specification, Pageable pageable);
    long count();

    List<TaskHistory> findByTaskIdOrderByRecordedAtAsc(String taskId);

    List<TaskHistory> findByOperationId(String operationId);
}
