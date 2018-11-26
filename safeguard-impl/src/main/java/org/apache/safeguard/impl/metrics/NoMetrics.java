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
package org.apache.safeguard.impl.metrics;

import java.util.function.Supplier;

import javax.enterprise.inject.Vetoed;

@Vetoed
class NoMetrics implements FaultToleranceMetrics {
    @Override
    public Counter counter(final String name, final String description) {
        return new Counter() {
            @Override
            public void inc() {
                // no-op
            }

            @Override
            public void dec() {
                // no-op
            }
        };
    }

    @Override
    public void gauge(final String name, final String description, final String unit,
                      final Supplier<Long> supplier) {
        // no-op
    }

    @Override
    public Histogram histogram(final String name, final String description) {
        return value -> {
            // no-op
        };
    }
}
