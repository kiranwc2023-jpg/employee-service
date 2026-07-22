package com.company.employee.service;

import com.company.employee.model.Employee;
import com.company.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * EmployeeService — the Business Logic Layer.
 *
 * PURPOSE OF THIS LAYER:
 *   Controllers should be thin (HTTP only): parse request, call service, return response.
 *   Repositories should be thin (DB only): CRUD operations.
 *   Services hold ALL business rules: validation, transformation, orchestration.
 *
 * Why this separation matters:
 *   If you put business logic in the controller, you cannot reuse it from another
 *   entry point (message queue, batch job, scheduled task).
 *   The service layer is entry-point-agnostic — it doesn't know or care about HTTP.
 */

// @Slf4j is a Lombok annotation that generates a static final logger:
//   private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
// This uses SLF4J (Simple Logging Facade for Java) — an abstraction layer.
// The actual logging backend is Logback (bundled with spring-boot-starter-web).
// You call log.info(), log.warn(), log.error() without caring which backend is used.
// Remove @Slf4j → "log" is undefined, all log statements fail to compile.
@Slf4j

// @Service tells Spring IoC: "Register this class as a singleton bean."
// It is semantically identical to @Component but communicates intent:
// "This class contains business logic."
// Technically you could replace @Service with @Component and nothing breaks.
// But @Service makes the architecture explicit and readable.
@Service

// @RequiredArgsConstructor (Lombok) generates a constructor for all
// fields marked as 'final'. This is CONSTRUCTOR INJECTION — the preferred
// dependency injection method in Spring.
//
// WHY constructor injection over @Autowired on fields?
//   1. The dependency cannot be null — constructor guarantees it.
//   2. The class is testable without Spring: new EmployeeService(mockRepo).
//   3. Dependencies are visible and explicit — not hidden inside private fields.
//   4. Spring team officially recommends constructor injection since Spring 4.3.
@RequiredArgsConstructor
public class EmployeeService {

    // 'final' + @RequiredArgsConstructor = constructor injection.
    // Spring finds the EmployeeRepository bean in the IoC container
    // and passes it to the constructor when creating EmployeeService.
    // Remove 'final' → Lombok doesn't inject it; field stays null → NullPointerException.
    private final EmployeeRepository employeeRepository;

    // ─────────────────────────────────────────────────────────────────
    // RETRIEVAL OPERATIONS
    // ─────────────────────────────────────────────────────────────────

    // @Transactional(readOnly = true)
    //   @Transactional:          Wraps the method in a database transaction.
    //   readOnly = true:         Hints to the database driver and Hibernate
    //                            that no write operations will occur.
    //                            This enables optimizations:
    //                            - Hibernate skips dirty-checking (change detection)
    //                            - Some DBs use read replicas for read-only transactions
    //                            - Reduces lock contention
    // Best practice: ALL query methods should be readOnly=true.
    // Remove readOnly=true → queries still work but are slower; dirty-check overhead.
    @Transactional(readOnly = true)
    public List<Employee> getAllEmployees() {
        log.info("Fetching all employees");
        // Delegates to JpaRepository.findAll() — SELECT * FROM employees
        return employeeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Employee> getEmployeeById(Long id) {
        log.info("Fetching employee with id: {}", id);
        // {} is SLF4J's placeholder — safer and faster than string concatenation.
        // String concatenation always runs even if the log level is WARN.
        // SLF4J placeholder evaluation is deferred — only runs if INFO is enabled.
        return employeeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Employee> getEmployeeByEmail(String email) {
        log.info("Fetching employee with email: {}", email);
        return employeeRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Employee> getEmployeesByDepartment(String department) {
        log.info("Fetching employees in department: {}", department);
        return employeeRepository.findByDepartment(department);
    }

    // ─────────────────────────────────────────────────────────────────
    // WRITE OPERATIONS (no readOnly — default is read-write transaction)
    // ─────────────────────────────────────────────────────────────────

    // @Transactional without readOnly = read-write transaction.
    // If an exception is thrown inside, the entire transaction is ROLLED BACK.
    // This is the "all or nothing" guarantee of ACID databases.
    @Transactional
    public Employee createEmployee(Employee employee) {
        log.info("Creating new employee: {} {}", employee.getFirstName(), employee.getLastName());

        // Business rule: email addresses must be unique across all employees.
        // We enforce this at the application layer (before the DB unique constraint fires).
        // This gives a clean 409 Conflict response instead of a raw DB error.
        if (employeeRepository.existsByEmail(employee.getEmail())) {
            // RuntimeException propagates out of @Transactional — triggers rollback.
            // IllegalArgumentException signals a caller mistake (bad input data).
            throw new IllegalArgumentException(
                "Employee with email '" + employee.getEmail() + "' already exists"
            );
        }

        // JpaRepository.save() performs:
        //   - If employee.getId() is null  → INSERT INTO employees (...)
        //   - If employee.getId() is set   → UPDATE employees SET ... WHERE id = ?
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created successfully with id: {}", savedEmployee.getId());
        return savedEmployee;
    }

    @Transactional
    public Optional<Employee> updateEmployee(Long id, Employee updatedEmployee) {
        log.info("Updating employee with id: {}", id);

        // findById returns Optional<Employee>.
        // map() applies the function only if a value is present (employee found).
        // If empty, map() returns Optional.empty() — no NullPointerException.
        return employeeRepository.findById(id).map(existingEmployee -> {
            // Only update fields that are provided — Patch-like behavior.
            existingEmployee.setFirstName(updatedEmployee.getFirstName());
            existingEmployee.setLastName(updatedEmployee.getLastName());
            existingEmployee.setDepartment(updatedEmployee.getDepartment());
            existingEmployee.setSalary(updatedEmployee.getSalary());
            existingEmployee.setStatus(updatedEmployee.getStatus());
            // We do NOT update email here — changing an email is a sensitive
            // operation that should go through a verification flow.

            // save() is called explicitly here even though Hibernate "dirty checking"
            // would auto-detect the change at transaction commit.
            // Explicit save() is more readable and self-documenting.
            Employee saved = employeeRepository.save(existingEmployee);
            log.info("Employee updated successfully: {}", id);
            return saved;
        });
    }

    @Transactional
    public boolean deleteEmployee(Long id) {
        log.warn("Deleting employee with id: {}", id);
        // log.warn for destructive operations — alerts monitoring systems.

        if (employeeRepository.existsById(id)) {
            employeeRepository.deleteById(id);
            log.info("Employee deleted: {}", id);
            return true;
        }
        log.warn("Attempted to delete non-existent employee: {}", id);
        return false;
    }

    @Transactional(readOnly = true)
    public long getTotalEmployeeCount() {
        return employeeRepository.count();
    }
}
