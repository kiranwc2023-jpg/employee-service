package com.company.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * EmployeeServiceApplication — the single entry point for the entire microservice.
 *
 * When the JVM starts, it calls main(). SpringApplication.run() bootstraps
 * the entire Spring IoC container, starts Tomcat, loads all beans,
 * registers all endpoints, and opens port 8080 for HTTP traffic.
 */
@SpringBootApplication
public class EmployeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeServiceApplication.class, args);
    }

}
