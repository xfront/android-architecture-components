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

package com.android.example.github.ui.repo;

import android.arch.lifecycle.ViewModel;
import android.support.annotation.VisibleForTesting;

import com.android.example.github.repository.RepoRepository;
import com.android.example.github.vo.Contributor;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.Resource;
import com.google.common.base.Optional;

import java.util.List;

import javax.inject.Inject;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;

public class RepoViewModel extends ViewModel {
    @VisibleForTesting
    final PublishSubject<RepoId> repoId;
    private final Flowable<Resource<Optional<Repo>>> repo;
    private final Flowable<Resource<List<Contributor>>> contributors;

    @Inject
    public RepoViewModel(RepoRepository repository) {
        this.repoId = PublishSubject.create();
        repo = repoId.toFlowable(BackpressureStrategy.LATEST).flatMap(input -> repository.loadRepo(input.owner, input.name));
        contributors = repoId.toFlowable(BackpressureStrategy.LATEST).flatMap(input -> repository.loadContributors(input.owner, input.name));

    }

    public Flowable<Resource<Optional<Repo>>> getRepo() {
        return repo;
    }

    public Flowable<Resource<List<Contributor>>> getContributors() {
        return contributors;
    }

    public void retry() {
        repo.retry(1);
        contributors.retry(1);
    }

    @VisibleForTesting
    public void setId(String owner, String name) {
        RepoId update = new RepoId(owner, name);
        repoId.onNext(update);
    }

    @VisibleForTesting
    static class RepoId {
        public final String owner;
        public final String name;

        RepoId(String owner, String name) {
            this.owner = owner == null ? null : owner.trim();
            this.name = name == null ? null : name.trim();
        }

        boolean isEmpty() {
            return owner == null || name == null || owner.length() == 0 || name.length() == 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RepoId repoId = (RepoId) o;

            if (owner != null ? !owner.equals(repoId.owner) : repoId.owner != null) {
                return false;
            }
            return name != null ? name.equals(repoId.name) : repoId.name == null;
        }

        @Override
        public int hashCode() {
            int result = owner != null ? owner.hashCode() : 0;
            result = 31 * result + (name != null ? name.hashCode() : 0);
            return result;
        }
    }
}
