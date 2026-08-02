package com.arkana.observability;

import static com.arkana.observability.ArkanaMetric.REPOSITORY_QUERY_COUNT;
import static com.arkana.observability.ArkanaMetric.REPOSITORY_QUERY_DURATION;

import com.arkana.domain.ProfileEntity;

import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RepositoryQueryLoggingAspect {
    private static final String REPOSITORY_PACKAGE = "com.arkana.repository";

    private final ArkanaMetricsService metrics;

    @Around("execution(* com.arkana.repository..*(..))")
    public Object aroundRepositoryQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.currentTimeMillis();
        Class<?> repositoryClass = resolveRepositoryClass(joinPoint);
        String signature = repositoryClass.getSimpleName() + "." + joinPoint.getSignature().getName();
        Timer.Sample timer = metrics.startTimer();
        metrics.incrementCounter(REPOSITORY_QUERY_COUNT, "query", signature);
        Throwable failure = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long duration = System.currentTimeMillis() - startedAt;
            metrics.stopTimer(timer, REPOSITORY_QUERY_DURATION, "query", signature);
            logQuery(joinPoint, signature, duration, failure);
        }
    }

    private void logQuery(
            ProceedingJoinPoint joinPoint,
            String signature,
            long duration,
            Throwable failure) {
        String parameters = parameterValues(joinPoint);
        if (failure != null) {
            log.error(
                    "Query {} failed after {} ms. Parameters: '{}'.",
                    signature,
                    duration,
                    parameters,
                    failure);
        } else if (duration < 1000) {
            log.trace("Query {} took {} ms. Parameters: '{}'.", signature, duration, parameters);
        } else if (duration < 2000) {
            log.debug("Query {} took {} ms. Parameters: '{}'.", signature, duration, parameters);
        } else if (duration < 3000) {
            log.info("Query {} took {} ms. Parameters: '{}'.", signature, duration, parameters);
        } else {
            log.warn("Query {} took {} ms. Parameters: '{}'.", signature, duration, parameters);
        }
    }

    private String parameterValues(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] parameterValues = joinPoint.getArgs();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < parameterValues.length; index++) {
            String parameterName = parameterNames != null && index < parameterNames.length
                    ? parameterNames[index]
                    : "arg" + index;
            result.append(parameterName)
                    .append('=')
                    .append(safeValue(parameterName, parameterValues[index]));
            if (index < parameterValues.length - 1) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    private Object safeValue(String name, Object value) {
        if (value == null) {
            return "null";
        }
        String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
        if (normalizedName.contains("email")
                || normalizedName.contains("password")
                || normalizedName.contains("secret")
                || normalizedName.contains("token")) {
            return "[REDACTED]";
        }
        if (value instanceof CharSequence) {
            return "[TEXT]";
        }
        if (value instanceof Pageable pageable) {
            return pageable;
        }
        if (value instanceof Collection<?> collection) {
            return value.getClass().getSimpleName() + "[size=" + collection.size() + "]";
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[size=" + Array.getLength(value) + "]";
        }
        Package valuePackage = value.getClass().getPackage();
        if (valuePackage != null && valuePackage.getName().startsWith("com.arkana.domain")) {
            return value.getClass().getSimpleName();
        }
        return value;
    }

    private Class<?> resolveRepositoryClass(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return resolveFromType(joinPoint.getThis())
                .or(() -> resolveFromType(joinPoint.getTarget()))
                .orElse(signature.getDeclaringType());
    }

    private Optional<Class<?>> resolveFromType(Object target) {
        if (target == null) {
            return Optional.empty();
        }
        return Arrays.stream(ClassUtils.getAllInterfacesForClass(target.getClass()))
                .filter(this::isRepositoryInterface)
                .findFirst();
    }

    private boolean isRepositoryInterface(Class<?> candidate) {
        String packageName = candidate.getPackageName();
        return packageName.equals(REPOSITORY_PACKAGE)
                || packageName.startsWith(REPOSITORY_PACKAGE + ".");
    }
}
