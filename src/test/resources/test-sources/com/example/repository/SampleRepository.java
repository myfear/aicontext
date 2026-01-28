package com.example.repository;

/**
 * Sample repository with security rules.
 * 
 * @aicontext-decision [2024-01-05] Using JPA for data access
 * @aicontext-rule All queries must use prepared statements
 * @aicontext-context SQL injection prevention is critical
 */
public class SampleRepository {
    
    /**
     * Find user by ID.
     * 
     * @aicontext-rule Never log sensitive user data
     * @param id user ID
     * @return user object
     */
    public Object findById(Long id) {
        return null;
    }
}
