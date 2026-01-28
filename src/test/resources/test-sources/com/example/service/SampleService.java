package com.example.service;

/**
 * Sample service class with multiple tags.
 * 
 * @aicontext-decision [2024-01-10] Using Spring for dependency injection
 * @aicontext-rule All service methods must be transactional
 * @aicontext-rule Never throw checked exceptions, use runtime exceptions
 * @aicontext-context GDPR compliance required for all data operations
 */
public class SampleService {
    
    /**
     * Process payment with business rules.
     * 
     * @aicontext-rule Rate limit: max 100 requests per minute
     * @param amount payment amount
     */
    public void processPayment(double amount) {
        // Implementation
    }
}
