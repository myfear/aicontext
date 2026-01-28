package com.example;

/**
 * Sample class for testing AIContext tag extraction.
 * 
 * @aicontext-decision [2024-01-15] Using this pattern for testing tag extraction
 * @aicontext-rule Always follow this rule in tests
 * @aicontext-context Important business context for testing
 */
public class SampleClass {
    
    private String field;
    
    /**
     * Sample method with implementation-level tags.
     * 
     * @aicontext-rule Method-level rule for testing
     * @param input test parameter
     * @return test result
     */
    public String sampleMethod(String input) {
        return input;
    }
    
    /**
     * Method with decision tag.
     * 
     * @aicontext-decision [2024-01-20] Using this algorithm for performance
     */
    public void anotherMethod() {
        // Implementation
    }
}
