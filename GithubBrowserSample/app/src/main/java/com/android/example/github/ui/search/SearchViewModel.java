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

package com.android.example.github.ui.search;

import android.arch.lifecycle.ViewModel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.example.github.repository.RepoRepository;
import com.android.example.github.util.Objects;
import com.android.example.github.vo.Repo;
import com.android.example.github.vo.Resource;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class SearchViewModel extends ViewModel {

    private final PublishSubject<String> query = PublishSubject.create();
    private final Flowable<Resource<List<Repo>>> results;
    private final NextPageHandler nextPageHandler;
    private String input;

    @Inject
    SearchViewModel(RepoRepository repoRepository) {
        nextPageHandler = new NextPageHandler(repoRepository);

        results = query.toFlowable(BackpressureStrategy.LATEST).flatMap(r -> repoRepository.search(r));
    }

    @VisibleForTesting
    public Flowable<Resource<List<Repo>>> getResults() {
        return results;
    }

    public void setQuery(@NonNull String originalInput) {
        String input = originalInput.toLowerCase(Locale.getDefault()).trim();
        if (TextUtils.isEmpty(input))
            return;
        nextPageHandler.reset();
        this.input = input;
        query.onNext(input);
    }

    @VisibleForTesting
    public Observable<LoadMoreState> getLoadMoreStatus() {
        return nextPageHandler.getLoadMoreState();
    }

    @VisibleForTesting
    public void loadNextPage() {
        nextPageHandler.queryNextPage(input);
    }

    void refresh() {
        query.replay();
    }

    static class LoadMoreState {
        private final boolean running;
        private final String errorMessage;
        private boolean handledError = false;

        LoadMoreState(boolean running, String errorMessage) {
            this.running = running;
            this.errorMessage = errorMessage;
        }

        boolean isRunning() {
            return running;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getErrorMessageIfNotHandled() {
            if (handledError) {
                return null;
            }
            handledError = true;
            return errorMessage;
        }
    }

    @VisibleForTesting
    static class NextPageHandler {
        private final PublishSubject<LoadMoreState> loadMoreState = PublishSubject.create();
        private final RepoRepository repository;
        @VisibleForTesting
        boolean hasMore;
        private String query;

        @VisibleForTesting
        NextPageHandler(RepoRepository repository) {
            this.repository = repository;
            reset();
        }

        void queryNextPage(String query) {
            if (Objects.equals(this.query, query)) {
                return;
            }

            this.query = query;

            loadMoreState.onNext(new LoadMoreState(true, null));
            //noinspection ConstantConditions
            repository.searchNextPage(query)
                    .subscribe(r -> {
                        this.onChanged(r);
                    });
        }


        public void onChanged(@Nullable Resource<Boolean> result) {
            if (result == null) {
                reset();
            } else {
                switch (result.status) {
                    case SUCCESS:
                        hasMore = Boolean.TRUE.equals(result.data);
                        loadMoreState.onNext(new LoadMoreState(false, null));
                        break;
                    case ERROR:
                        hasMore = true;
                        loadMoreState.onNext(new LoadMoreState(false,
                                result.message));
                        break;
                }
                loadMoreState.onComplete();
            }
        }

        private void reset() {
            hasMore = true;
            loadMoreState.onNext(new LoadMoreState(false, null));
        }

        Observable<LoadMoreState> getLoadMoreState() {
            return loadMoreState;
        }
    }
}
