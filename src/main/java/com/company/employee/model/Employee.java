package com.company.employee.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;

/**
 * Employee — the JPA Entity that maps directly to a database table named "employees".
 *
 * This class serves TWO purposes simultaneously:
 *   1. JPA Entity: Spring Data uses this to create/manage the DB table.
 *   2. Domain Object: passed through service and controller layers.
 *
 * In larger projects you would separate these into Entity + DTO,
 * but for this demo they are merged for clarity.
 */

// @Entity tells Hibernate: "Map this Java class to a relational database table."
// Without @Entity, this class is just a POJO — JPA ignores it completely.
@Entity

// @Table(name="employees") explicitly names the database table.
// Without this, Hibernate derives the table name from the class name.
// "Employee" would become table "employee" (Hibernate default).
// Explicit naming is a best practice — you control the DB schema, not Hibernate's naming strategy.
@Table(name = "employees")

// @Data is a Lombok meta-annotation that generates:
//   @Getter       — public getters for all fields
//   @Setter       — public setters for all non-final fields
//   @ToString     — human-readable toString()
//   @EqualsAndHashCode — equals() and hashCode() based on all fields
//   @RequiredArgsConstructor — constructor for all final/@NonNull fields
// Without @Data you write ~80 lines of boilerplate by hand.
@Data

// @Builder generates the Builder pattern:
//   Employee emp = Employee.builder().firstName("John").salary(50000).build();
// This is preferred over telescoping constructors.
@Builder

// @NoArgsConstructor generates Employee() — a constructor with no arguments.
// JPA REQUIRES a no-arg constructor. Without it:
//   javax.persistence.PersistenceException: No default constructor for entity
@NoArgsConstructor

// @AllArgsConstructor generates Employee(Long id, String firstName, ...).
// Required by @Builder when @NoArgsConstructor is also present.
@AllArgsConstructor
public class Employee {

    // @Id marks this field as the PRIMARY KEY of the database table.
    // Without @Id, Hibernate throws: "No identifier specified for entity: Employee"
    @Id

    // @GeneratedValue(strategy = GenerationType.IDENTITY) tells the database
    // to auto-increment the ID column (like AUTO_INCREMENT in MySQL, SERIAL in Postgres,
    // or IDENTITY in H2/SQL Server).
    // Without this, you must manually set the ID before every save() — error-prone.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column configures how this Java field maps to a database column.
    //   name="first_name"  → the column in the DB is called "first_name" (snake_case)
    //   nullable=false     → NOT NULL constraint in the database DDL
    //   length=100         → VARCHAR(100) in the database
    // Without @Column, Hibernate maps the field to a column named "firstName" (camelCase).
    @Column(name = "first_name", nullable = false, length = 100)

    // @NotBlank is a Bean Validation (JSR-380) constraint.
    // It checks that the value is not null AND not empty AND not whitespace-only.
    // This runs BEFORE the data reaches the database.
    // @NotNull only checks null. @NotEmpty allows whitespace. @NotBlank is strictest.
    // message="..." defines the error message returned in API validation errors.
    @NotBlank(message = "First name is required")

    // @Size validates the string length at the application layer (before the DB).
    // Even though the DB column is VARCHAR(100), this gives a cleaner validation error
    // instead of a raw database constraint violation.
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    // @Email validates that the string matches the email format: user@domain.tld
    // This is a format check only — it does NOT verify the email address exists.
    @Column(name = "email", nullable = false, unique = true, length = 255)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @Column(name = "department", length = 100)
    private String department;

    // @Column without nullable=false → the column allows NULL in the database.
    // @Positive ensures salary > 0 if provided. A salary of 0 or negative is rejected.
    @Column(name = "salary")
    @Positive(message = "Salary must be a positive number")
    private Double salary;

    // LocalDate maps to SQL DATE type (date only, no time component).
    // This is a Java 8 Time API type — fully supported by modern Hibernate.
    @Column(name = "hire_date")
    private LocalDate hireDate;

    // @Enumerated(EnumType.STRING) tells Hibernate to store the enum's NAME ("ACTIVE", "INACTIVE")
    // rather than the ordinal number (0, 1).
    // EnumType.ORDINAL is dangerous: if you reorder enum constants, existing DB data is corrupted.
    // EnumType.STRING is always safe — the DB stores the human-readable name.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    // Nested enum for type safety. Only ACTIVE, INACTIVE, ON_LEAVE are valid values.
    // Using an enum prevents "typo" bugs like status = "Actve".
    public enum EmployeeStatus {
        ACTIVE, INACTIVE, ON_LEAVE
    }

}
