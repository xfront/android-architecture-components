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

package com.android.example.github.ui.user;

import com.android.example.github.repository.RepoRepository;
import com.android.example.github.repository.UserRepository;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.Resource;
import com.android.example.github.vo.User;
import com.google.common.base.Optional;

import android.arch.lifecycle.ViewModel;
import android.support.annotation.VisibleForTesting;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;

public class UserViewModel extends ViewModel {
    @VisibleForTesting
    final PublishSubject<String> login = PublishSubject.create();
    private final Flowable<Resource<List<Repo>>> repositories;
    private final Flowable<Resource<Optional<User>>> user;
    @SuppressWarnings("unchecked")
    @Inject
    public UserViewModel(UserRepository userRepository, RepoRepository repoRepository) {
        user = login.toFlowable(BackpressureStrategy.LATEST).flatMap(r -> userRepository.loadUser(r));
        repositories = login.toFlowable(BackpressureStrategy.LATEST).flatMap(r -> repoRepository.loadRepos(r));
    }

    @VisibleForTesting
    public void setLogin(String login) {
        this.login.onNext(login);
    }

    @VisibleForTesting
    public Flowable<Resource<Optional<User>>> getUser() {
        return user;
    }

    @VisibleForTesting
    public Flowable<Resource<List<Repo>>> getRepositories() {
        return repositories;
    }

    @VisibleForTesting
    public void retry() {
        this.login.retry(1);
    }
}
