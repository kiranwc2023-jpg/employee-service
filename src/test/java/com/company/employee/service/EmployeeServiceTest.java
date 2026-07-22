package com.company.employee.service;

import com.company.employee.model.Employee;
import com.company.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * EmployeeServiceTest — Unit Tests for the Business Logic Layer.
 *
 * UNIT TEST PRINCIPLES:
 *   - Tests ONE class in isolation (EmployeeService)
 *   - ALL dependencies (EmployeeRepository) are MOCKED
 *   - No database, no HTTP, no Spring context
 *   - Extremely fast (milliseconds, not seconds)
 *   - Tests behavior, not implementation
 *
 * Testing Pyramid:
 *   Unit Tests    (many,   fast,   cheap)   ← this file
 *   Integration Tests (some,  medium, medium)
 *   End-to-End Tests  (few,   slow,   expensive)
 */

// @ExtendWith(MockitoExtension.class) integrates Mockito with JUnit 5.
// It enables @Mock, @InjectMocks, and @Spy annotations.
// Without this extension, @Mock fields are null and tests throw NullPointerException.
// In JUnit 4, you needed @RunWith(MockitoJUnitRunner.class) — JUnit 5 uses @ExtendWith.
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    // @Mock creates a Mockito mock object — a fake implementation of EmployeeRepository.
    // The mock:
    //   - Has all methods of EmployeeRepository
    //   - By default, every method returns the "default" value (null, 0, false, empty Optional)
    //   - You configure specific behaviors with when().thenReturn()
    //
    // WHY MOCK?
    //   Real EmployeeRepository needs a database connection.
    //   In a unit test, we don't want to spin up H2, Hibernate, JPA.
    //   We want to test ONLY the EmployeeService logic — nothing else.
    @Mock
    private EmployeeRepository employeeRepository;

    // @InjectMocks creates an instance of EmployeeService and injects ALL @Mock fields.
    // Mockito first tries constructor injection, then setter, then field injection.
    // EmployeeService uses constructor injection (@RequiredArgsConstructor),
    // so Mockito finds the constructor that takes EmployeeRepository and injects the mock.
    @InjectMocks
    private EmployeeService employeeService;

    // The Employee instance reused across tests.
    // Initialized in @BeforeEach to ensure a clean state per test.
    private Employee testEmployee;

    // @BeforeEach runs BEFORE EVERY single @Test method in this class.
    // Use it for common setup that each test needs.
    // If tests share a mutable object and one test modifies it,
    // @BeforeEach ensures the next test gets a fresh copy.
    @BeforeEach
    void setUp() {
        // Builder pattern from @Builder annotation on Employee
        testEmployee = Employee.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@company.com")
                .department("Engineering")
                .salary(75000.0)
                .status(Employee.EmployeeStatus.ACTIVE)
                .build();
    }

    // @Nested allows grouping related tests into inner classes.
    // This improves readability: all "create" tests are in CreateEmployee,
    // all "get" tests are in GetEmployee, etc.
    // The test report shows: EmployeeServiceTest > CreateEmployee > should create employee successfully
    @Nested
    @DisplayName("Create Employee Tests")
    class CreateEmployee {

        // @Test marks a method as a test case. JUnit 5 discovers and runs it.
        // @DisplayName overrides the method name in test reports.
        // "should_CreateEmployee_WhenEmailIsUnique" → "should create new employee successfully"
        @Test
        @DisplayName("should create new employee successfully when email is unique")
        void shouldCreateEmployee_WhenEmailIsUnique() {
            // ─── ARRANGE ─────────────────────────────────────────────
            // "Arrange, Act, Assert" (AAA) pattern — the standard unit test structure.

            // when(...).thenReturn(...) sets up mock behavior:
            //   "When employeeRepository.existsByEmail(any email) is called,
            //    return false (email does NOT exist)"
            // any(String.class) matches any String argument — we don't care which email.
            // This simulates: "no employee with this email exists yet"
            when(employeeRepository.existsByEmail(any(String.class))).thenReturn(false);

            // When save() is called with any Employee, return testEmployee.
            // This simulates: "save succeeded, the DB assigned ID=1 and returned the row"
            when(employeeRepository.save(any(Employee.class))).thenReturn(testEmployee);

            // ─── ACT ─────────────────────────────────────────────────
            // Call the actual method we're testing
            Employee result = employeeService.createEmployee(testEmployee);

            // ─── ASSERT ──────────────────────────────────────────────
            // assertThat() is from AssertJ — a fluent assertion library.
            // It's more readable than JUnit's assertEquals(expected, actual).
            //
            // assertThat(result).isNotNull()
            //   Checks: result != null
            //   If null → AssertionError: "Expecting actual not to be null"
            assertThat(result).isNotNull();

            // assertThat(result.getId()).isEqualTo(1L)
            //   Checks: result.getId() == 1L
            //   If not → AssertionError shows expected vs actual values
            assertThat(result.getId()).isEqualTo(1L);

            // Check the employee's name was preserved
            assertThat(result.getFirstName()).isEqualTo("John");
            assertThat(result.getEmail()).isEqualTo("john.doe@company.com");

            // verify() asserts that a mock method was called with specific arguments.
            // verify(employeeRepository).existsByEmail("john.doe@company.com")
            //   → confirms that existsByEmail was called exactly once with that email
            //   → if not called: AssertionError: "Wanted but not invoked"
            //   → if called with wrong args: AssertionError: argument mismatch
            verify(employeeRepository).existsByEmail("john.doe@company.com");

            // verify the save was also called exactly once
            verify(employeeRepository).save(any(Employee.class));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when email already exists")
        void shouldThrowException_WhenEmailAlreadyExists() {
            // ARRANGE: email EXISTS → service should reject the creation
            when(employeeRepository.existsByEmail(any(String.class))).thenReturn(true);

            // ACT + ASSERT combined:
            // assertThatThrownBy(() -> ...) asserts that the lambda THROWS an exception.
            // .isInstanceOf(IllegalArgumentException.class) checks the exception type.
            // .hasMessageContaining("already exists") checks the message.
            assertThatThrownBy(() -> employeeService.createEmployee(testEmployee))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            // verify that save() was NEVER called when email is duplicate
            // verifyNoMoreInteractions is a strict check — no unexpected calls
            verify(employeeRepository, never()).save(any(Employee.class));
        }
    }

    @Nested
    @DisplayName("Get Employee Tests")
    class GetEmployee {

        @Test
        @DisplayName("should return employee when found by ID")
        void shouldReturnEmployee_WhenFoundById() {
            // ARRANGE: findById returns our test employee
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(testEmployee));

            // ACT
            Optional<Employee> result = employeeService.getEmployeeById(1L);

            // ASSERT
            // assertThat(result).isPresent() checks Optional is not empty
            assertThat(result).isPresent();

            // .get() extracts the value from Optional (safe here because we asserted isPresent)
            assertThat(result.get().getFirstName()).isEqualTo("John");

            verify(employeeRepository).findById(1L);
        }

        @Test
        @DisplayName("should return empty Optional when employee not found")
        void shouldReturnEmpty_WhenEmployeeNotFound() {
            // ARRANGE: findById returns empty Optional (employee doesn't exist)
            when(employeeRepository.findById(999L)).thenReturn(Optional.empty());

            // ACT
            Optional<Employee> result = employeeService.getEmployeeById(999L);

            // ASSERT
            // assertThat(result).isEmpty() checks Optional.isEmpty() == true
            // This verifies the service correctly propagates "not found" from the repository
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return all employees")
        void shouldReturnAllEmployees() {
            // ARRANGE: findAll returns a list with our test employee
            when(employeeRepository.findAll()).thenReturn(List.of(testEmployee));

            // ACT
            List<Employee> result = employeeService.getAllEmployees();

            // ASSERT
            // assertThat(result).hasSize(1) checks List.size() == 1
            assertThat(result).hasSize(1);

            // assertThat(result).contains(testEmployee) checks the list contains the element
            assertThat(result).contains(testEmployee);
        }
    }

    @Nested
    @DisplayName("Delete Employee Tests")
    class DeleteEmployee {

        @Test
        @DisplayName("should return true when employee is deleted successfully")
        void shouldReturnTrue_WhenEmployeeDeleted() {
            // ARRANGE
            when(employeeRepository.existsById(1L)).thenReturn(true);
            // doNothing() is the default behavior for void methods, but explicit for clarity
            doNothing().when(employeeRepository).deleteById(1L);

            // ACT
            boolean result = employeeService.deleteEmployee(1L);

            // ASSERT
            // assertThat(result).isTrue() is a Boolean assertion
            assertThat(result).isTrue();
            verify(employeeRepository).deleteById(1L);
        }

        @Test
        @DisplayName("should return false when employee does not exist")
        void shouldReturnFalse_WhenEmployeeNotFound() {
            // ARRANGE
            when(employeeRepository.existsById(999L)).thenReturn(false);

            // ACT
            boolean result = employeeService.deleteEmployee(999L);

            // ASSERT
            assertThat(result).isFalse();

            // verify deleteById was NEVER called — deleting a non-existent employee
            // should not even attempt the deletion
            verify(employeeRepository, never()).deleteById(any());
        }
    }
}
