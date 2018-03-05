/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.github.repository;

import android.support.annotation.NonNull;

import com.android.example.github.vo.Resource;
import com.google.common.util.concurrent.AbstractScheduledService;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

/**
 * A generic class that can provide a resource backed by both the sqlite database and the network.
 * <p>
 * You can read more about it in the <a href="https://developer.android.com/arch">Architecture
 * Guide</a>.
 *
 * @param <DBType>
 * @param <NetType>
 */
public abstract class NetworkBoundResource<DBType, NetType> {

    public Flowable<Resource<DBType>> load() {
        return Flowable.create(emitter -> {
            emitter.onNext(Resource.loading(null));
            Flowable<DBType> dbSource = loadFromDb();
            dbSource.subscribeOn(Schedulers.io())
                    .subscribe(r -> {
                        if (shouldFetch(r)) {
                            emitter.onNext(Resource.loading(null));
                            fetchFromNet()
                                    .retry(2)
                                    .subscribe(this::saveCallResult,
                                            e -> {
                                                onFetchFailed(e);
                                                emitter.onNext(Resource.error(e.getMessage(), null));
                                            }
                                    );
                        } else {
                            emitter.onNext(Resource.success(r));
                        }
                    },
                    e -> emitter.onNext(Resource.error(e.getMessage(), null)),
                    () -> emitter.onComplete());
        }, BackpressureStrategy.LATEST);
    }

    protected void onFetchFailed(Throwable e) {

    }

    @NonNull
    protected abstract Flowable<NetType> fetchFromNet();

    protected abstract void saveCallResult(@NonNull NetType item);

    protected abstract boolean shouldFetch(@NonNull DBType data);

    @NonNull
    protected abstract Flowable<DBType> loadFromDb();
}
