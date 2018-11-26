package org.apache.safeguard.impl.cdi;

import static java.util.stream.Collectors.toList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessBean;
import javax.enterprise.inject.spi.WithAnnotations;

import org.apache.safeguard.impl.config.GeronimoFaultToleranceConfig;
import org.apache.safeguard.impl.customizable.Safeguard;
import org.apache.safeguard.impl.fallback.FallbackInterceptor;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

// todo: mp.fault.tolerance.interceptor.priority handling
public class SafeguardExtension implements Extension {
    private boolean foundExecutor;

    void addFallbackInterceptor(@Observes final ProcessAnnotatedType<FallbackInterceptor> processAnnotatedType) {
        processAnnotatedType.configureAnnotatedType().add(new FallbackBinding());
    }

    void activateSafeguard(@Observes @WithAnnotations({
            /*Asynchronous.class, */
            Bulkhead.class, CircuitBreaker.class,
            Fallback.class, Retry.class, Timeout.class
    }) final ProcessAnnotatedType<?> processAnnotatedType) {
        if (processAnnotatedType.getAnnotatedType().getJavaClass().getName().startsWith("org.apache.safeguard.impl")) {
            return;
        }
        if (faultToleranceAnnotations().anyMatch(it -> processAnnotatedType.getAnnotatedType().isAnnotationPresent(it))) {
            processAnnotatedType.configureAnnotatedType().add(SafeguardEnabled.Literal.INSTANCE);
        } else {
            final List<Method> methods = processAnnotatedType.getAnnotatedType().getMethods().stream()
                    .filter(it -> faultToleranceAnnotations().anyMatch(it::isAnnotationPresent))
                    .map(AnnotatedMethod::getJavaMember).collect(toList());
            processAnnotatedType.configureAnnotatedType()
                    .filterMethods(it -> methods.contains(it.getJavaMember()))
                    .forEach(m -> m.add(SafeguardEnabled.Literal.INSTANCE));
        }
    }

    void onBean(@Observes final ProcessBean<?> bean) {
        if (bean.getBean().getQualifiers().stream().anyMatch(it -> it.annotationType() == Safeguard.class)
            && bean.getBean().getTypes().stream().anyMatch(it -> Executor.class.isAssignableFrom(toClass(it)))) {
            foundExecutor = true;
        }
    }

    void addMissingBeans(@Observes final AfterBeanDiscovery afterBeanDiscovery) {
        final GeronimoFaultToleranceConfig config = GeronimoFaultToleranceConfig.create();
        afterBeanDiscovery.addBean()
                .id("geronimo_safeguard#configuration")
                .types(GeronimoFaultToleranceConfig.class, Object.class)
                .beanClass(GeronimoFaultToleranceConfig.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .createWith(c -> config);

        afterBeanDiscovery.addBean()
                .id("geronimo_safeguard#metrics")
                .types(FaultToleranceMetrics.class, Object.class)
                .beanClass(FaultToleranceMetrics.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(ApplicationScoped.class)
                .createWith(c -> FaultToleranceMetrics.create());

        if (!foundExecutor) {
            afterBeanDiscovery.addBean()
                    .id("geronimo_safeguard#executor")
                    .types(Executor.class, Object.class)
                    .beanClass(Executor.class)
                    .qualifiers(Safeguard.Literal.INSTANCE, Any.Literal.INSTANCE)
                    .createWith(c -> Executors.newCachedThreadPool())
                    .scope(ApplicationScoped.class)
                    .destroyWith((e, c) -> ExecutorService.class.cast(e).shutdownNow());
        }
    }

    public Class<?> toClass(final Type it) {
        return doToClass(it, 0);
    }

    private Class<?> doToClass(final Type it, final int iterations) {
        if (Class.class.isInstance(it)) {
            return Class.class.cast(it);
        }
        if (iterations > 100) { // with generic it happens we can loop here
            return Object.class;
        }
        if (ParameterizedType.class.isInstance(it)) {
            return doToClass(ParameterizedType.class.cast(it).getRawType(), iterations + 1);
        }
        return Object.class; // will not match any of our test
    }

    private Stream<Class<? extends Annotation>> faultToleranceAnnotations() {
        return Stream.of(Asynchronous.class, Bulkhead.class, CircuitBreaker.class,
                Fallback.class, Retry.class, Timeout.class);
    }
}
