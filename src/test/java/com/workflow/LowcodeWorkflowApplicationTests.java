package com.workflow;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LowcodeWorkflowApplicationTests {

    @Test
    void mainMethodShouldExist() throws Exception {
        Method method = LowcodeWorkflowApplication.class.getMethod("main", String[].class);
        assertNotNull(method);
    }
}
