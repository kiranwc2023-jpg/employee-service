package com.company.employee.controller;

import com.company.employee.model.Employee;
import com.company.employee.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * EmployeeControllerTest — Slice Test for the HTTP / Web Layer.
 *
 * DIFFERENCE FROM EmployeeServiceTest:
 *   EmployeeServiceTest   = pure unit test (no Spring, no HTTP)
 *   EmployeeControllerTest = slice test (Spring Web MVC only, no real DB)
 *
 * @WebMvcTest loads ONLY the web layer components:
 *   - DispatcherServlet
 *   - EmployeeController (the class under test)
 *   - Jackson for JSON serialization
 *   - Bean Validation
 * It does NOT load:
 *   - JPA/Hibernate (no database)
 *   - Service layer (we mock EmployeeService)
 *   - Full application context
 *
 * This makes @WebMvcTest tests much faster than @SpringBootTest.
 */

// @WebMvcTest(EmployeeController.class)
//   Loads Spring MVC infrastructure but ONLY for EmployeeController.
//   All other beans (EmployeeService, EmployeeRepository) must be mocked.
//   Without this annotation, MockMvc is not configured and @Autowired MockMvc fails.
@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    // @Autowired injects a Spring-managed bean into this test class.
    // MockMvc is the "fake" HTTP client that sends requests to the controller
    // without actually starting a real HTTP server on a port.
    //
    // MockMvc simulates: DispatcherServlet → HandlerMapping → Controller → ResponseBody
    // This gives you realistic HTTP behavior (status codes, headers, body) without network.
    @Autowired
    private MockMvc mockMvc;

    // @MockBean is Spring's version of Mockito's @Mock.
    // It creates a Mockito mock AND registers it as a Spring bean in the test context.
    // @WebMvcTest needs EmployeeController, which requires EmployeeService.
    // Without @MockBean, Spring fails to create EmployeeController: "No qualifying bean of type EmployeeService"
    @MockBean
    private EmployeeService employeeService;

    // ObjectMapper is Jackson's main class for JSON serialization/deserialization.
    // We use it to convert Employee objects to JSON strings for POST request bodies.
    // Spring Boot auto-configures ObjectMapper as a bean.
    @Autowired
    private ObjectMapper objectMapper;

    private Employee testEmployee;

    @BeforeEach
    void setUp() {
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

    // ─────────────────────────────────────────────────────────────────
    // TEST: GET /api/v1/employees returns 200 OK with employee list
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("GET /api/v1/employees returns 200 with list of employees")
    void getAllEmployees_ReturnsOkWithList() throws Exception {
        // ARRANGE: when service.getAllEmployees() is called, return our test list
        when(employeeService.getAllEmployees()).thenReturn(List.of(testEmployee));

        // ACT + ASSERT: perform HTTP request and assert the response
        mockMvc.perform(
                // get("/api/v1/employees") builds a GET request to that URL
                get("/api/v1/employees")
                // .accept(MediaType.APPLICATION_JSON) sets the Accept header
                // → tells the server we expect JSON in response
                .accept(MediaType.APPLICATION_JSON)
            )
            // .andExpect() chains assertions on the response

            // status().isOk() asserts HTTP 200 OK
            // Fails with: "Status expected:<200> but was:<400>"
            .andExpect(status().isOk())

            // content().contentType(APPLICATION_JSON) asserts Content-Type: application/json
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

            // jsonPath() uses JSONPath expression syntax to query the response JSON
            // "$" = root of the JSON document
            // "$" with isArray() checks the root is a JSON array
            .andExpect(jsonPath("$", hasSize(1)))

            // "$[0].firstName" = first element of the array, "firstName" field
            // is("John") checks the value equals "John"
            .andExpect(jsonPath("$[0].firstName", is("John")))
            .andExpect(jsonPath("$[0].email", is("john.doe@company.com")));
    }

    // ─────────────────────────────────────────────────────────────────
    // TEST: GET /api/v1/employees/1 returns 200 OK with employee
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("GET /api/v1/employees/{id} returns 200 when employee exists")
    void getEmployeeById_ReturnsOkWhenFound() throws Exception {
        when(employeeService.getEmployeeById(1L)).thenReturn(Optional.of(testEmployee));

        mockMvc.perform(get("/api/v1/employees/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // "$.id" = the "id" field at the root of the JSON object
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.department", is("Engineering")));
    }

    // ─────────────────────────────────────────────────────────────────
    // TEST: GET /api/v1/employees/999 returns 404 Not Found
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("GET /api/v1/employees/{id} returns 404 when employee not found")
    void getEmployeeById_Returns404WhenNotFound() throws Exception {
        // Service returns empty Optional → controller returns 404
        when(employeeService.getEmployeeById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/employees/999").accept(MediaType.APPLICATION_JSON))
                // status().isNotFound() asserts HTTP 404 Not Found
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────
    // TEST: POST /api/v1/employees creates employee and returns 201
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("POST /api/v1/employees returns 201 Created with saved employee")
    void createEmployee_Returns201WithCreatedEmployee() throws Exception {
        when(employeeService.createEmployee(any(Employee.class))).thenReturn(testEmployee);

        // objectMapper.writeValueAsString(testEmployee) converts Employee object
        // to a JSON string: {"id":1,"firstName":"John","lastName":"Doe",...}
        String requestBody = objectMapper.writeValueAsString(testEmployee);

        mockMvc.perform(
                // post("/api/v1/employees") builds a POST request
                post("/api/v1/employees")
                // .contentType(APPLICATION_JSON) sets Content-Type header
                // → tells the server the request body is JSON
                .contentType(MediaType.APPLICATION_JSON)
                // .content(requestBody) sets the request body
                .content(requestBody)
            )
            // status().isCreated() asserts HTTP 201 Created
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(1)))
            .andExpect(jsonPath("$.firstName", is("John")));
    }

    // ─────────────────────────────────────────────────────────────────
    // TEST: POST with invalid data returns 400 Bad Request
    // Bean Validation triggers when @Valid rejects the input
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("POST /api/v1/employees returns 400 when firstName is blank")
    void createEmployee_Returns400WhenInvalidInput() throws Exception {
        // Create an invalid employee with blank firstName
        Employee invalidEmployee = Employee.builder()
                .firstName("")          // @NotBlank violation
                .lastName("Doe")
                .email("not-an-email")  // @Email violation
                .build();

        mockMvc.perform(
                post("/api/v1/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmployee))
            )
            // @Valid catches blank firstName and invalid email → 400 Bad Request
            // Spring auto-handles MethodArgumentNotValidException → HTTP 400
            .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────
    // TEST: DELETE /api/v1/employees/1 returns 204 No Content
    // ─────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("DELETE /api/v1/employees/{id} returns 204 when successfully deleted")
    void deleteEmployee_Returns204WhenDeleted() throws Exception {
        when(employeeService.deleteEmployee(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/employees/1"))
                // status().isNoContent() asserts HTTP 204 No Content
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/employees/{id} returns 404 when employee not found")
    void deleteEmployee_Returns404WhenNotFound() throws Exception {
        when(employeeService.deleteEmployee(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/employees/999"))
                .andExpect(status().isNotFound());
    }
}
