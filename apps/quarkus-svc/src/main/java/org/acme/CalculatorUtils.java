package org.acme;

import org.jboss.logging.Logger;

public class CalculatorUtils {

    // 1. 声明并初始化 Logger，这是 Quarkus 推荐的日志框架 (JBoss Logging)
    private static final Logger LOG = Logger.getLogger(CalculatorUtils.class);

    public static int add(int a, int b) {
        LOG.infof("Executing add method with params: a=%d, b=%d", a, b);
        int result = a + b;
        LOG.debugf("The result of %d + %d is %d", a, b, result);
        return result; 
    }

    public static int divide(int a, int b) {
        LOG.info("Executing divide method...");
        if (b == 0) {
            LOG.error("Math violation: Attempted to divide by zero!");
            throw new IllegalArgumentException("Cannot divide by zero!");
        }
        return a / b;
    }
}
