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

package com.android.example.github.api;

import com.android.example.github.vo.Contributor;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.User;

import java.util.List;

import io.reactivex.Flowable;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * REST API access points
 */
public interface GithubService {
    @GET("users/{login}")
    Flowable<User> getUser(@Path("login") String login);

    @GET("users/{login}/repos")
    Flowable<List<Repo>> getRepos(@Path("login") String login);

    @GET("repos/{owner}/{name}")
    Flowable<Repo> getRepo(@Path("owner") String owner, @Path("name") String name);

    @GET("repos/{owner}/{name}/contributors")
    Flowable<List<Contributor>> getContributors(@Path("owner") String owner, @Path("name") String name);

    @GET("search/repositories")
    Flowable<Response<RepoSearchResponse>> searchRepos(@Query("q") String query);

    @GET("search/repositories")
    Flowable<Response<RepoSearchResponse>> searchRepos(@Query("q") String query, @Query("page") int page);
}
