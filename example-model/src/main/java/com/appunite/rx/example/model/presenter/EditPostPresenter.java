package com.appunite.rx.example.model.presenter;

import com.appunite.rx.ObservableExtensions;
import com.appunite.rx.ResponseOrError;
import com.appunite.rx.dagger.UiScheduler;
import com.appunite.rx.example.model.dao.PostsDao;
import com.appunite.rx.example.model.model.PostWithBody;
import com.appunite.rx.operators.OnSubscribeCombineLatestWithoutBackPressure;
import com.appunite.rx.operators.OperatorSampleWithLastWithObservable;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import retrofit.client.Response;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class EditPostPresenter {

    @Nonnull
    private final Scheduler uiScheduler;
    @Nonnull
    private final PostsDao postDao;
    @Nonnull
    private final PublishSubject<String> nameSubject = PublishSubject.create();
    @Nonnull
    private final PublishSubject<String> bodySubject = PublishSubject.create();
    @Nonnull
    private final PublishSubject<Object> sendSubject = PublishSubject.create();
    @Nonnull
    private final Observable<ResponseOrError<Response>> postUpdated;
    @Nonnull
    private final PublishSubject<Object> navigationClickSubject = PublishSubject.create();
    @Nonnull
    private final String postId;
    @Nonnull
    private final Observable<ResponseOrError<String>> nameObservable;
    @Nonnull
    private final Observable<ResponseOrError<String>> bodyObservable;

    public EditPostPresenter(@Nonnull @UiScheduler Scheduler uiScheduler,
                             @Nonnull PostsDao postDao,
                             @Nonnull String id) {

        this.uiScheduler = uiScheduler;
        this.postDao = postDao;
        this.postId = id;

        Observable.just(postId)
                .subscribe(postDao.postIdObserver());

        nameObservable = postDao.getPostWithBodyObservable()
                .compose(ResponseOrError.map(new Func1<PostWithBody, String>() {
                    @Override
                    public String call(PostWithBody item) {
                        return Strings.nullToEmpty(item.name());
                    }
                }))
                .compose(ObservableExtensions.<ResponseOrError<String>>behaviorRefCount());

        bodyObservable = postDao.getPostWithBodyObservable()
                .compose(ResponseOrError.map(new Func1<PostWithBody, String>() {
                    @Override
                    public String call(PostWithBody item) {
                        return Strings.nullToEmpty(item.body());
                    }
                }))
                .compose(ObservableExtensions.<ResponseOrError<String>>behaviorRefCount());


        OnSubscribeCombineLatestWithoutBackPressure.combineLatest(
                nameSubject,
                bodySubject,
                new Func2<String, String, PostWithBody>() {
                    @Override
                    public PostWithBody call(String name, String body) {
                        return new PostWithBody(postId, name, body);
                    }
                })
                .lift(OperatorSampleWithLastWithObservable.<PostWithBody>create(sendSubject))
                .subscribe(postDao.updatePostRequestObserver());

        postUpdated = postDao.getUpdatedPostObservable();
    }

    @Nonnull
    public Observer<String> nameObserver() {
        return nameSubject;
    }

    @Nonnull
    public Observer<String> bodyObserver() {
        return bodySubject;
    }

    @Nonnull
    public Observer<Object> sendObserver() {
        return sendSubject;
    }

    @Nonnull
    public Observable<String> nameObservable() {
        return nameObservable.compose(ResponseOrError.<String>onlySuccess());
    }

    @Nonnull
    public Observable<String> bodyObservable() {
        return bodyObservable.compose(ResponseOrError.<String>onlySuccess());
    }

    @Nonnull
    public Observable<Response> finishActivityObservable() {
        return this.postUpdated.compose(ResponseOrError.<Response>onlySuccess());
    }

    @Nonnull
    public Observable<Throwable> postErrorObservable() {
        return this.postUpdated.compose(ResponseOrError.<Response>onlyError());
    }

    @Nonnull
    public Observable<Throwable> errorObservable() {
        return ResponseOrError.combineErrorsObservable(ImmutableList.of(
                ResponseOrError.transform(postUpdated)))
                .distinctUntilChanged();

    }

    public Observable<Object> closeActivityObservable() {
        return navigationClickSubject;
    }

    public Observer<Object> navigationClickObserver() {
        return navigationClickSubject;
    }

//    public Observable<Object> startPostponedEnterTransitionObservable() {
//        final Observable<Boolean> filter = progressObservable().filter(Functions1.isFalse());
//        final Observable<Throwable> error = errorObservable().filter(Functions1.isNotNull());
//        final Observable<String> timeout = Observable.just("").delay(500, TimeUnit.MILLISECONDS, uiScheduler);
//        return Observable.<Object>amb(filter, error, timeout).first();
//    }
}
