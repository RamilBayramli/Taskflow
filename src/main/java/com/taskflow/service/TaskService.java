package com.taskflow.service;

import com.taskflow.dto.TaskForm;
import com.taskflow.entity.Category;
import com.taskflow.entity.Priority;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.TaskRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {
    private static final Comparator<Task> BY_DUE_DATE =
        Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));

    private final TaskRepository taskRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, CategoryService categoryService, Clock clock) {
        this.taskRepository = taskRepository;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Task owned(Long id, Long userId) {
        return taskRepository.findByIdAndUserId(id, userId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<Task> all(Long userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Task save(Long id, User user, TaskForm form) {
        Task task = id == null ? new Task() : owned(id, user.getId());
        Long categoryId = form.getCategoryId();
        Category category = categoryId == null ? null : categoryService.owned(categoryId, user.getId());
        String description = form.getDescription();

        task.setTitle(form.getTitle().strip());
        task.setDescription(description == null ? "" : description.strip());
        task.setPriority(form.getPriority());
        task.setDueDate(form.getDueDate());
        task.setCategory(category);
        task.setUser(user);
        return taskRepository.save(task);
    }

    public void toggle(Long id, Long userId) {
        Task task = owned(id, userId);
        task.setCompleted(!task.isCompleted());
    }

    public void delete(Long id, Long userId) {
        taskRepository.delete(owned(id, userId));
    }

    @Transactional(readOnly = true)
    public List<Task> search(
        Long userId,
        String query,
        String status,
        Long categoryId,
        Priority priority,
        String sort
    ) {
        LocalDate today = LocalDate.now(clock);
        List<Task> matches = taskRepository.findAll((root, criteria, builder) -> {
            root.fetch("category", JoinType.LEFT);
            List<Predicate> filters = new ArrayList<>();
            filters.add(builder.equal(root.get("user").get("id"), userId));

            if (query != null && !query.isBlank()) {
                String pattern = containsPattern(query);
                filters.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("description")), pattern, '\\')
                ));
            }

            switch (status) {
                case "active" -> filters.add(builder.isFalse(root.get("completed")));
                case "completed" -> filters.add(builder.isTrue(root.get("completed")));
                case "today" -> filters.add(builder.equal(root.get("dueDate"), today));
                case "overdue" -> {
                    filters.add(builder.isFalse(root.get("completed")));
                    filters.add(builder.lessThan(root.get("dueDate"), today));
                }
                case null, default -> {
                    // Other statuses leave the result unfiltered by status.
                }
            }

            if (categoryId != null) {
                filters.add(builder.equal(root.get("category").get("id"), categoryId));
            }
            if (priority != null) {
                filters.add(builder.equal(root.get("priority"), priority));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        });

        Comparator<Task> taskOrder = switch (sort) {
            case "title" -> Comparator.comparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER);
            case "priority" -> Comparator.comparing(Task::getPriority).reversed();
            case "newest" -> Comparator.comparing(Task::getCreatedAt).reversed();
            case null, default -> BY_DUE_DATE;
        };
        matches.sort(taskOrder.thenComparing(Task::getId));
        return matches;
    }

    private static String containsPattern(String query) {
        String escapedQuery = query.strip()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escapedQuery + "%";
    }

    public Dashboard dashboard(List<Task> tasks) {
        LocalDate today = LocalDate.now(clock);
        long completed = tasks.stream().filter(Task::isCompleted).count();
        long overdue = tasks.stream().filter(task -> task.isOverdue(today)).count();
        List<Task> tasksDueToday = tasks.stream()
            .filter(task -> today.equals(task.getDueDate()))
            .sorted(Comparator.comparing(Task::isCompleted)
                .thenComparing(Task::getPriority, Comparator.reverseOrder()))
            .toList();
        long completedToday = tasksDueToday.stream().filter(Task::isCompleted).count();
        List<Task> active = tasks.stream()
            .filter(task -> !task.isCompleted())
            .sorted(BY_DUE_DATE.thenComparing(Task::getPriority, Comparator.reverseOrder()))
            .toList();
        List<Task> upcoming = active.stream().limit(6).toList();
        List<Task> otherActive = active.stream()
            .filter(task -> !today.equals(task.getDueDate()))
            .limit(6)
            .toList();
        List<DayProgress> week = weekProgress(tasks, today);
        long weekMaximum = Math.max(1, week.stream().mapToLong(DayProgress::count).max().orElse(1));
        long weekCompleted = week.stream().mapToLong(DayProgress::count).sum();

        return new Dashboard(
            tasks.size(), completed, tasks.size() - completed, overdue,
            percent(completed, tasks.size()),
            tasksDueToday.size(), completedToday, percent(completedToday, tasksDueToday.size()),
            tasksDueToday.stream().limit(6).toList(), active, upcoming, otherActive, week, weekMaximum, weekCompleted
        );
    }

    private List<DayProgress> weekProgress(List<Task> tasks, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<DayProgress> week = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = monday.plusDays(offset);
            long count = tasks.stream()
                .map(Task::getCompletedAt)
                .filter(completedAt -> completedAt != null)
                .filter(completedAt -> completedAt.atZone(clock.getZone()).toLocalDate().equals(date))
                .count();
            week.add(new DayProgress(date, count, date.equals(today)));
        }
        return week;
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * part / total);
    }

    public List<CalendarDay> calendar(YearMonth month, List<Task> tasks) {
        Map<LocalDate, Long> tasksByDueDate = tasks.stream()
            .filter(task -> task.getDueDate() != null)
            .collect(Collectors.groupingBy(Task::getDueDate, Collectors.counting()));
        LocalDate today = LocalDate.now(clock);
        LocalDate firstOfMonth = month.atDay(1);
        LocalDate firstCalendarDay = firstOfMonth.minusDays(firstOfMonth.getDayOfWeek().getValue() - 1);
        List<CalendarDay> days = new ArrayList<>();
        for (int offset = 0; offset < 42; offset++) {
            LocalDate date = firstCalendarDay.plusDays(offset);
            days.add(new CalendarDay(
                date,
                YearMonth.from(date).equals(month),
                date.equals(today),
                tasksByDueDate.getOrDefault(date, 0L)
            ));
        }
        return days;
    }

    public record Dashboard(
        long total,
        long completed,
        long active,
        long overdue,
        int percent,
        long dailyTotal,
        long dailyDone,
        int dailyPercent,
        List<Task> todayTasks,
        List<Task> activeTasks,
        List<Task> upcoming,
        List<Task> otherActive,
        List<DayProgress> week,
        long weekMaximum,
        long weekCompleted
    ) {
    }

    public record DayProgress(LocalDate date, long count, boolean today) {
    }

    public record CalendarDay(LocalDate date, boolean inMonth, boolean today, long count) {
    }
}
