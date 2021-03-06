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

import com.android.example.github.api.ApiResponse;
import com.android.example.github.api.GithubService;
import com.android.example.github.api.RepoSearchResponse;
import com.android.example.github.db.GithubDb;
import com.android.example.github.vo.RepoSearchResult;
import com.android.example.github.vo.Resource;
import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

/**
 * A task that reads the search result in the database and fetches the next page, if it has one.
 */
public class FetchNextSearchPageTask implements Callable {
    private final String query;
    private final GithubService githubService;
    private final GithubDb db;

    FetchNextSearchPageTask(String query, GithubService githubService, GithubDb db) {
        this.query = query;
        this.githubService = githubService;
        this.db = db;
    }

    public Flowable<Resource<Boolean>> call() {
        return Flowable.fromCallable(()-> Optional.fromNullable(db.repoDao().findSearchResult(query)))
                .subscribeOn(Schedulers.io())
                .flatMap(sr -> {
                    if (!sr.isPresent())
                        return Flowable.empty();

                    final RepoSearchResult current = sr.get();
                    if (current.next == null) {
                        return Flowable.just(Resource.success(false));
                    }

                    return githubService.searchRepos(query, current.next).map(r -> {
                        ApiResponse<RepoSearchResponse> apiResponse = new ApiResponse<RepoSearchResponse>(r);
                        if (apiResponse.isSuccessful()) {
                            RepoSearchResponse item = apiResponse.body;
                            // we merge all repo ids into 1 list so that it is easier to fetch the result list.
                            List<Integer> ids = new ArrayList<>();
                            ids.addAll(current.repoIds);
                            //noinspection ConstantConditions
                            ids.addAll(item.getRepoIds());
                            RepoSearchResult merged = new RepoSearchResult(query, ids,
                                    item.getTotal(), apiResponse.getNextPage());
                            try {
                                db.beginTransaction();
                                db.repoDao().insert(merged);
                                db.repoDao().insertRepos(item.getItems());
                                db.setTransactionSuccessful();
                            } finally {
                                db.endTransaction();
                            }
                            return (Resource.success(apiResponse.getNextPage() != null));
                        } else {
                            return Resource.error(apiResponse.errorMessage, false);
                        }
                    });
                });
    }

    ;
}
