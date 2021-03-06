/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.safeguard.impl.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.apache.safeguard.impl.cache.Key;
import org.apache.safeguard.impl.cache.UnwrappedCache;
import org.apache.safeguard.impl.cdi.SafeguardEnabled;
import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;

// simple way to ensure we use a single key in the interceptor context by call
// and avoids to manage a stack
@Interceptor
@SafeguardEnabled
@Priority(Interceptor.Priority.PLATFORM_BEFORE)
public class IdGeneratorInterceptor implements Serializable {
    private final AtomicLong idGenerator = new AtomicLong();

    @Inject
    private Cache cache;

    @AroundInvoke
    public Object generateId(final InvocationContext context) throws Exception {
        final long id = idGenerator.incrementAndGet();
        context.getContextData().put(IdGeneratorInterceptor.class.getName(), id);

        final Map<Key, Counters> counters = cache.getCounters();
        final Key key = new Key(context, cache.getUnwrappedCache().getUnwrappedCache());
        // todo: ?context.getContextData().put(IdGeneratorInterceptor.class.getName() + ".key_" + id, key);

        Counters methodCounters = counters.get(key);
        if (methodCounters == null) {
            methodCounters = cache.create(context.getMethod());
            final Counters existing = counters.putIfAbsent(key, methodCounters);
            if (existing != null) {
                methodCounters = existing;
            }
        }

        methodCounters.total.inc();
        try {
            final Object proceed = context.proceed();
            if (CompletionStage.class.isInstance(proceed)) {  // todo: integrate with futures
                final Counters countersRef = methodCounters;
                final CompletionStage<?> completionStage = CompletionStage.class.cast(proceed);
                return completionStage.exceptionally(e -> {
                    countersRef.failed.inc();
                    if (RuntimeException.class.isInstance(e)) {
                        throw RuntimeException.class.cast(e);
                    }
                    if (Error.class.isInstance(e)) {
                        throw Error.class.cast(e);
                    }
                    throw new IllegalStateException(e);
                });
            }
            return proceed;
        } catch (final Exception | Error e) {
            methodCounters.failed.inc();
            throw e;
        }
    }

    private static class Counters {
        private final FaultToleranceMetrics.Counter total;
        private final FaultToleranceMetrics.Counter failed;

        private Counters(final FaultToleranceMetrics.Counter total, final FaultToleranceMetrics.Counter failed) {
            this.total = total;
            this.failed = failed;
        }
    }

    @ApplicationScoped
    public static class Cache {
        private final Map<Key, Counters> counters = new ConcurrentHashMap<>();

        @Inject
        private FaultToleranceMetrics metrics;

        @Inject
        private UnwrappedCache unwrappedCache;

        public UnwrappedCache getUnwrappedCache() {
            return unwrappedCache;
        }

        public Map<Key, Counters> getCounters() {
            return counters;
        }

        public Counters create(final Method method) {
            //ft.org.eclipse.microprofile.fault.tolerance.tck.metrics.RetryMetricBean.failSeveralTimes.invocations.total
            final String metricsNameBase = "ft." + method.getDeclaringClass().getCanonicalName() + "." + method.getName() + ".invocations.";
            return new Counters(
                    metrics.counter(metricsNameBase + "total",
                            "The number of times the method was called"),
                    metrics.counter(metricsNameBase + "failed.total",
                            "The number of times the method was called and, after all Fault Tolerance actions had been processed, threw a Throwable"));
        }
    }
}
