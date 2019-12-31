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
package org.apache.safeguard.impl.retry;

import java.util.Map;

import javax.annotation.Priority;
import javax.interceptor.Interceptor;

import org.apache.safeguard.impl.metrics.FaultToleranceMetrics;
import org.eclipse.microprofile.faulttolerance.Retry;

@Retry
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_AFTER)
public class BeforeRetryInterceptor extends BaseRetryInterceptor {
    @Override
    protected boolean suspendBulkhead() {
        return false;
    }

    @Override
    protected void executeFinalCounterAction(final Map<String, Object> contextData,
                                             final FaultToleranceMetrics.Counter counter) {
        counter.inc();
    }
}
