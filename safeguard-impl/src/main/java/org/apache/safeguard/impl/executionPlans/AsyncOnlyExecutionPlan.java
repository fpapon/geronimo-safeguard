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

package org.apache.safeguard.impl.executionPlans;

import org.apache.safeguard.exception.AsyncException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

class AsyncOnlyExecutionPlan implements ExecutionPlan {
    private final ExecutorService executorService;

    AsyncOnlyExecutionPlan(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public <T> T execute(Callable<T> callable) {
        Future<T> submitted = executorService.submit(callable);
        try {
            return submitted.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new AsyncException(e);
        }
    }
}
