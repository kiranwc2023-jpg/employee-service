package com.company.employee.controller;

import com.company.employee.model.Employee;
import com.company.employee.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * EmployeeController — the HTTP Layer (Presentation Layer).
 *
 * RESPONSIBILITIES:
 *   1. Parse incoming HTTP requests (URL path, query params, request body)
 *   2. Validate request data (delegate to Bean Validation via @Valid)
 *   3. Delegate to EmployeeService for business logic
 *   4. Return the appropriate HTTP response (status code + body)
 *
 * This class should contain ZERO business logic.
 * If you're writing if-else for business rules here, move it to the service.
 */
@Slf4j

// @RestController = @Controller + @ResponseBody
//
// @Controller: Marks this class as a Spring MVC controller — registered in DispatcherServlet.
// @ResponseBody: Every method return value is automatically serialized to JSON (via Jackson)
//               and written directly to the HTTP response body.
//               Without @ResponseBody, Spring MVC tries to find a "view" (HTML template)
//               matching the returned string — which fails for REST APIs.
//
// @RestController was introduced in Spring 4.0 specifically for REST APIs.
// Before that, you needed @Controller + @ResponseBody on every method.
@RestController

// @RequestMapping("/api/v1/employees") sets the BASE URL for ALL methods in this class.
// Every endpoint method's URL is appended to this base.
//
// Why "/api/v1/"?
//   /api  → distinguishes API endpoints from static assets, error pages, etc.
//   /v1   → API versioning. When you introduce breaking changes, you create /v2
//           and keep /v1 alive for existing clients. Without versioning, breaking
//           changes break all clients simultaneously.
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    // ─────────────────────────────────────────────────────────────────
    // GET /api/v1/employees
    // Returns all employees.
    // HTTP 200 OK with JSON array body.
    // ─────────────────────────────────────────────────────────────────

    // @GetMapping is shorthand for @RequestMapping(method = RequestMethod.GET)
    // It binds this method to HTTP GET requests at the class-level URL.
    // Other method-specific mappings: @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        log.info("GET /api/v1/employees - Fetching all employees");
        List<Employee> employees = employeeService.getAllEmployees();

        // ResponseEntity<T> is Spring's wrapper for a full HTTP response.
        // It lets you control:
        //   - HTTP status code (200, 201, 404, 409, etc.)
        //   - Response headers
        //   - Response body
        //
        // ResponseEntity.ok(body) = HTTP 200 + body serialized as JSON
        // Alternatives: ResponseEntity.status(HttpStatus.OK).body(employees)
        return ResponseEntity.ok(employees);
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/{id}
    // Returns one employee by ID or 404 if not found.
    // ─────────────────────────────────────────────────────────────────

    // @GetMapping("/{id}") appends "/{id}" to the class-level mapping.
    // Full URL: GET /api/v1/employees/{id}
    // {id} is a URL path variable — a dynamic segment captured by @PathVariable.
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(

        // @PathVariable extracts the {id} segment from the URL path.
        // For request: GET /api/v1/employees/42
        // Spring extracts "42", converts it to Long, injects it as 'id'.
        // If the value cannot be converted to Long (e.g., /employees/abc),
        // Spring returns HTTP 400 Bad Request automatically.
        @PathVariable Long id) {

        log.info("GET /api/v1/employees/{} - Fetching employee", id);

        // map(ResponseEntity::ok) transforms Optional<Employee> to Optional<ResponseEntity>
        //   → if employee found: Optional<ResponseEntity.ok(employee)>
        // orElse(ResponseEntity.notFound().build()) is the fallback
        //   → if empty Optional: HTTP 404 with no body
        //
        // This pattern avoids null checks completely.
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────
    // POST /api/v1/employees
    // Creates a new employee.
    // HTTP 201 Created with the saved employee in the body.
    // ─────────────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> createEmployee(

        // @RequestBody tells Spring to deserialize the HTTP request body (JSON)
        // into an Employee object using Jackson (the JSON library).
        // If the Content-Type header is not application/json, Spring returns HTTP 415.
        // If the JSON is malformed, Spring returns HTTP 400 automatically.
        //
        // @Valid triggers Bean Validation on the Employee object.
        // Spring validates all @NotBlank, @Email, @Size, @Positive annotations.
        // If validation fails, Spring throws MethodArgumentNotValidException
        // and returns HTTP 400 with validation error details.
        // Without @Valid, the annotations on Employee are IGNORED.
        @Valid @RequestBody Employee employee) {

        log.info("POST /api/v1/employees - Creating employee: {} {}",
                 employee.getFirstName(), employee.getLastName());

        try {
            Employee created = employeeService.createEmployee(employee);

            // ResponseEntity.status(HttpStatus.CREATED).body(created)
            // = HTTP 201 Created + the newly created employee as JSON body.
            // HTTP 201 (not 200) signals: "resource was created." This is
            // the correct REST convention for POST that creates a resource.
            // Without .body(created), the response body is empty — the client
            // doesn't know the new employee's ID or other generated fields.
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            // The service threw IllegalArgumentException for duplicate email.
            // HTTP 409 Conflict = "the request conflicts with current state of server."
            // We return a structured error map — consistent error format.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // PUT /api/v1/employees/{id}
    // Updates an existing employee fully. Returns 200 or 404.
    // ─────────────────────────────────────────────────────────────────

    // @PutMapping("/{id}") handles HTTP PUT at URL /api/v1/employees/{id}
    // PUT = full replacement of the resource (all fields must be provided)
    // PATCH = partial update (only provided fields change)
    // This implementation behaves more like PATCH for email, but PUT semantically.
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody Employee employee) {

        log.info("PUT /api/v1/employees/{} - Updating employee", id);
        return employeeService.updateEmployee(id, employee)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────
    // DELETE /api/v1/employees/{id}
    // Deletes an employee. Returns 204 No Content or 404.
    // ─────────────────────────────────────────────────────────────────

    // @DeleteMapping handles HTTP DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        log.info("DELETE /api/v1/employees/{} - Deleting employee", id);

        boolean deleted = employeeService.deleteEmployee(id);

        if (deleted) {
            // HTTP 204 No Content = success, but no body to return.
            // This is the correct REST convention for successful DELETE.
            // The resource is gone — there's nothing to return in the body.
            return ResponseEntity.noContent().build();
        }
        // HTTP 404 Not Found = the resource to delete doesn't exist.
        return ResponseEntity.notFound().build();
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/department/{department}
    // Searches employees by department.
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/department/{department}")
    public ResponseEntity<List<Employee>> getByDepartment(@PathVariable String department) {
        log.info("GET /api/v1/employees/department/{} - Fetching by department", department);
        return ResponseEntity.ok(employeeService.getEmployeesByDepartment(department));
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /api/v1/employees/count
    // Returns total employee count.
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getEmployeeCount() {
        long count = employeeService.getTotalEmployeeCount();

        // Map.of() creates an immutable map in Java 9+.
        // Returns JSON: { "total": 42 }
        // This structured format is extensible — you can add more fields later.
        return ResponseEntity.ok(Map.of("total", count));
    }

}
