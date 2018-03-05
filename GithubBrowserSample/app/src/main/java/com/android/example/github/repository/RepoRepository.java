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
import android.support.annotation.Nullable;

import com.android.example.github.api.GithubService;
import com.android.example.github.api.RepoSearchResponse;
import com.android.example.github.db.GithubDb;
import com.android.example.github.db.RepoDao;
import com.android.example.github.util.RateLimiter;
import com.android.example.github.vo.Contributor;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.RepoSearchResult;
import com.android.example.github.vo.Resource;
import com.google.common.base.Optional;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Flowable;
import timber.log.Timber;

/**
 * Repository that handles Repo instances.
 * <p>
 * unfortunate naming :/ .
 * Repo - value object name
 * Repository - type of this class.
 */
@Singleton
public class RepoRepository {

    private final GithubDb db;

    private final RepoDao repoDao;

    private final GithubService githubService;


    private RateLimiter<String> repoListRateLimit = new RateLimiter<>(10, TimeUnit.MINUTES);

    @Inject
    public RepoRepository( GithubDb db, RepoDao repoDao,
                          GithubService githubService) {
        this.db = db;
        this.repoDao = repoDao;
        this.githubService = githubService;
    }

    public Flowable<Resource<List<Repo>>> loadRepos(String owner) {
        return new NetworkBoundResource<List<Repo>, List<Repo>>() {
            @Override
            protected void saveCallResult(@NonNull List<Repo> item) {
                repoDao.insertRepos(item);
            }

            @Override
            protected boolean shouldFetch(@Nullable List<Repo> data) {
                return data.isEmpty() || repoListRateLimit.shouldFetch(owner);
            }

            @NonNull
            @Override
            protected Flowable<List<Repo>> loadFromDb() {
                return repoDao.loadRepositories(owner);
            }

            @NonNull
            @Override
            protected Flowable<List<Repo>> fetchFromNet() {
                return githubService.getRepos(owner);
            }

            @Override
            protected void onFetchFailed(Throwable e) {
                repoListRateLimit.reset(owner);
            }
        }.load();
    }

    public Flowable<Resource<Optional<Repo>>> loadRepo(String owner, String name) {
        return new NetworkBoundResource<Optional<Repo>, Repo>() {
            @Override
            protected void saveCallResult(@NonNull Repo item) {
                repoDao.insert(item);
            }

            @Override
            protected boolean shouldFetch(@Nullable Optional<Repo> data) {
                return !data.isPresent();
            }

            @NonNull
            @Override
            protected Flowable<Optional<Repo>> loadFromDb() {
                return repoDao.load(owner, name);
            }

            @NonNull
            @Override
            protected Flowable<Repo> fetchFromNet() {
                return githubService.getRepo(owner, name);
            }
        }.load();
    }

    public Flowable<Resource<List<Contributor>>> loadContributors(String owner, String name) {
        return new NetworkBoundResource<List<Contributor>, List<Contributor>>() {
            @Override
            protected void saveCallResult(@NonNull List<Contributor> contributors) {
                for (Contributor contributor : contributors) {
                    contributor.setRepoName(name);
                    contributor.setRepoOwner(owner);
                }
                db.beginTransaction();
                try {
                    repoDao.createRepoIfNotExists(new Repo(Repo.UNKNOWN_ID,
                            name, owner + "/" + name, "",
                            new Repo.Owner(owner, null), 0));
                    repoDao.insertContributors(contributors);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                Timber.d("rece saved contributors to db");
            }

            @Override
            protected boolean shouldFetch(@Nullable List<Contributor> data) {
                Timber.d("rece contributor list from db: %s", data);
                return data == null || data.isEmpty();
            }

            @NonNull
            @Override
            protected Flowable<List<Contributor>> loadFromDb() {
                return repoDao.loadContributors(owner, name);
            }

            @NonNull
            @Override
            protected Flowable<List<Contributor>> fetchFromNet() {
                return githubService.getContributors(owner, name);
            }
        }.load();
    }

    public Flowable<Resource<Boolean>> searchNextPage(String query) {
        return new FetchNextSearchPageTask(query, githubService, db).call();
    }

    public Flowable<Resource<List<Repo>>> search(String query) {
        return new NetworkBoundResource<List<Repo>, RepoSearchResponse>() {

            @Override
            protected void saveCallResult(@NonNull RepoSearchResponse item) {
                List<Integer> repoIds = item.getRepoIds();
                RepoSearchResult repoSearchResult = new RepoSearchResult(
                        query, repoIds, item.getTotal(), item.getNextPage());
                db.beginTransaction();
                try {
                    repoDao.insertRepos(item.getItems());
                    repoDao.insert(repoSearchResult);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            @Override
            protected boolean shouldFetch(@Nullable List<Repo> data) {
                return data.isEmpty();
            }

            @NonNull
            @Override
            protected Flowable<List<Repo>> loadFromDb() {
                return repoDao.search(query).flatMap(r -> {
                            if (r.isPresent())
                                return repoDao.loadOrdered(r.get().repoIds);
                            else
                                return Flowable.just(Collections.emptyList());
                        }
                );
            }

            @NonNull
            @Override
            protected Flowable<RepoSearchResponse> fetchFromNet() {
                return githubService.searchRepos(query);
            }

        }.load();
    }
}
