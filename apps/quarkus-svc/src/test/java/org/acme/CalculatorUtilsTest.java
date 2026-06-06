package org.acme;

import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import org.jboss.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class CalculatorUtilsTest {

    // 在测试类里引入 Logger，用法和普通类一模一样
    private static final Logger LOG = Logger.getLogger(CalculatorUtilsTest.class);

    @Test
    public void testAdd() {
        LOG.info("====== Starting testAdd ======");
        int a = 5;
        int b = 10;
        
        LOG.infof("About to call CalculatorUtils.add(%d, %d)", a, b);
        int sum = CalculatorUtils.add(a, b); 
        
        LOG.infof("Got result: %d. Asserting it equals 15.", sum);
        assertEquals(15, sum, "5 + 10 should be 15");
        LOG.info("====== testAdd passed! ======");
    }

    @Test
    public void testDivide() {
        LOG.info("====== Starting testDivide ======");
        assertEquals(2, CalculatorUtils.divide(10, 5));
        
        LOG.info("Testing divide by zero exception...");
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            CalculatorUtils.divide(10, 0);
        });
        assertEquals("Cannot divide by zero!", exception.getMessage());
        LOG.info("====== testDivide passed! ======");
    }
}
