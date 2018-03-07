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

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.android.example.github.util.Objects;
import com.android.example.github.vo.Resource;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
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

/*
public abstract class NetworkBoundResource<DBType, NetType> {

    public LiveData<Resource<DBType>> load() {
        MutableLiveData liveData = new MutableLiveData();
        liveData.postValue(Resource.loading(null));
        Flowable.fromCallable(() -> loadFromDb())
                .subscribeOn(Schedulers.io())
                .subscribe(r -> {
                            if (shouldFetch(r.getValue())) {
                                liveData.postValue(Resource.loading(r));
                                fetchFromNet()
                                        .retry(2)
                                        .subscribe(this::saveCallResult,
                                                e -> {
                                                    onFetchFailed(e);
                                                    liveData.postValue(Resource.error(e.getMessage(), r));
                                                }
                                        );
                            } else {
                                liveData.postValue(Resource.success(r));
                            }
                        },
                        e -> liveData.postValue(Resource.error(e.getMessage(), null)));
        return liveData;
    }

    protected void onFetchFailed(Throwable e) {

    }

    @NonNull
    protected abstract Flowable<NetType> fetchFromNet();

    protected abstract void saveCallResult(@NonNull NetType item);

    protected abstract boolean shouldFetch(@NonNull DBType data);

    @NonNull
    protected abstract LiveData<DBType> loadFromDb();
}
*/
public abstract class NetworkBoundResource<DBType, NetType> {

    private final MediatorLiveData<Resource<DBType>> result = new MediatorLiveData<>();

    @MainThread
    NetworkBoundResource() {
        result.setValue(Resource.loading(null));
        LiveData<DBType> dbSource = loadFromDb();
        result.addSource(dbSource, data -> {
            result.removeSource(dbSource);
            if (shouldFetch(data)) {
                fetchFromNetwork(dbSource);
            } else {
                result.addSource(dbSource, newData -> setValue(Resource.success(newData)));
            }
        });
    }

    @MainThread
    private void setValue(Resource<DBType> newValue) {
        if (!Objects.equals(result.getValue(), newValue)) {
            result.setValue(newValue);
        }
    }

    private void fetchFromNetwork(final LiveData<DBType> dbSource) {
        Flowable<NetType> apiResponse = fetchFromNet();
        // we re-attach dbSource as a new source, it will dispatch its latest value quickly
        result.addSource(dbSource, newData -> setValue(Resource.loading(newData)));
        apiResponse.subscribeOn(Schedulers.io())
                .doOnNext(r -> saveCallResult(processResponse(r)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(r->{
                                result.removeSource(dbSource);
                                // we specially request a new live data,
                                // otherwise we will get immediately last cached value,
                                // which may not be updated with latest results received from network.
                                result.addSource(loadFromDb(),
                                        newData -> setValue(Resource.success(newData)));
                               }
                        , e -> {
                            onFetchFailed(e);
                            result.addSource(dbSource,
                                    newData -> setValue(Resource.error(e.getMessage(), newData)));
                            }
                    );
    }

    protected void onFetchFailed(Throwable e) {
    }

    public LiveData<Resource<DBType>> asLiveData() {
        return result;
    }

    @WorkerThread
    protected NetType processResponse(NetType response) {
        return response;
    }

    @WorkerThread
    protected abstract void saveCallResult(@NonNull NetType item);

    @MainThread
    protected abstract boolean shouldFetch(@Nullable DBType data);

    @NonNull
    @MainThread
    protected abstract LiveData<DBType> loadFromDb();

    @NonNull
    @MainThread
    protected abstract Flowable<NetType> fetchFromNet();
}
