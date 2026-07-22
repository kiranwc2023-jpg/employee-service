package com.company.employee.repository;

import com.company.employee.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * EmployeeRepository — the Data Access Layer (DAL).
 *
 * This interface extends JpaRepository. Spring Data JPA automatically generates
 * the implementation class at startup. You write ZERO SQL for standard CRUD.
 *
 * Why an interface instead of a class?
 * Spring Data uses Java Dynamic Proxy to create a concrete implementation at runtime.
 * The proxy reads your method names and generates the correct SQL queries.
 * This is called "query derivation" or "derived queries."
 */

// @Repository is a Spring stereotype annotation.
// It tells Spring: "Register this as a bean in the IoC container"
//                  "This class interacts with the persistence layer"
//                  "Translate raw JPA/JDBC exceptions into Spring's DataAccessException hierarchy"
//
// The last point is important: without @Repository, a SQLIntegrityConstraintViolationException
// from your database bubbles up as a raw vendor exception.
// WITH @Repository, it becomes a DataIntegrityViolationException — a Spring abstraction
// that is consistent regardless of whether you use H2, MySQL, Postgres, or Oracle.
//
// Note: When extending JpaRepository, @Repository is technically optional because
// JpaRepository itself is already annotated with @NoRepositoryBean and Spring Data
// registers it automatically. Adding @Repository explicitly is still a best practice
// for readability — it signals the layer to every reader.
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // JpaRepository<Employee, Long> gives you these methods for FREE:
    //   save(Employee e)          → INSERT or UPDATE
    //   findById(Long id)         → SELECT * WHERE id = ?  → returns Optional<Employee>
    //   findAll()                 → SELECT * FROM employees
    //   deleteById(Long id)       → DELETE WHERE id = ?
    //   count()                   → SELECT COUNT(*) FROM employees
    //   existsById(Long id)       → SELECT 1 WHERE id = ? (efficient existence check)
    //   saveAll(Iterable<E>)      → batch INSERT/UPDATE
    //   deleteAll()               → DELETE FROM employees (dangerous in prod!)
    //
    // The type parameters:
    //   Employee = the entity type this repository manages
    //   Long     = the type of the primary key (@Id field)

    // ─────────────────────────────────────────────────────────────────
    // DERIVED QUERY: Spring Data reads the method name and builds SQL.
    //
    // "findBy" → SELECT * FROM employees WHERE
    // "Email"  → email = ?
    //
    // Generated SQL: SELECT * FROM employees WHERE email = :email
    //
    // Returns Optional<Employee> — forces the caller to handle the
    // "not found" case explicitly instead of returning null.
    // Returning null is a bad practice that causes NullPointerExceptions.
    // ─────────────────────────────────────────────────────────────────
    Optional<Employee> findByEmail(String email);

    // Derived query: SELECT * FROM employees WHERE department = :department
    // Returns a List (may be empty, never null). This is the safe contract.
    List<Employee> findByDepartment(String department);

    // Derived query with two conditions combined with AND:
    // SELECT * FROM employees WHERE department = :department AND status = :status
    List<Employee> findByDepartmentAndStatus(String department, Employee.EmployeeStatus status);

    // Derived query: LIKE comparison
    // "Containing" maps to SQL: first_name LIKE '%firstName%'
    // "IgnoreCase" makes it case-insensitive: UPPER(first_name) LIKE UPPER('%firstName%')
    List<Employee> findByFirstNameContainingIgnoreCase(String firstName);

    // ─────────────────────────────────────────────────────────────────
    // JPQL QUERY: when derived queries become too complex, you write
    // JPQL (Java Persistence Query Language) — SQL-like but uses
    // class names and field names instead of table/column names.
    //
    // "Employee e" → the Entity class, not the table
    // "e.salary"   → the Java field, not the column name
    // :minSalary   → named parameter bound by @Param
    // ─────────────────────────────────────────────────────────────────
    @Query("SELECT e FROM Employee e WHERE e.salary >= :minSalary ORDER BY e.salary DESC")
    List<Employee> findEmployeesWithMinimumSalary(@Param("minSalary") Double minSalary);

    // JPQL with aggregation: COUNT grouped by department
    // Returns a List<Object[]> where each element is [department, count]
    // In production, you would map this to a DTO projection for type safety.
    @Query("SELECT e.department, COUNT(e) FROM Employee e GROUP BY e.department")
    List<Object[]> countByDepartment();

    // Existence check — more efficient than findByEmail().isPresent()
    // because the generated SQL uses SELECT 1 or SELECT COUNT(1)
    // rather than SELECT * (fetches no columns).
    boolean existsByEmail(String email);

}
