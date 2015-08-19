package com.appunite.rx.example;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.TextView;

import com.appunite.rx.android.MoreViewActions;
import com.appunite.rx.android.MoreViewObservables;
import com.appunite.rx.example.dagger.FakeDagger;
import com.appunite.rx.example.model.presenter.MainPresenter;

import java.util.List;

import javax.annotation.Nonnull;

import butterknife.ButterKnife;
import butterknife.InjectView;
import rx.Observable;
import rx.android.view.ViewActions;
import rx.android.view.ViewObservable;
import rx.functions.Action1;

public class MainActivity extends BaseActivity {

    @InjectView(R.id.main_activity_toolbar)
    Toolbar toolbar;
    @InjectView(R.id.main_activity_recycler_view)
    RecyclerView recyclerView;
    @InjectView(R.id.main_activity_progress)
    View progress;
    @InjectView(R.id.main_activity_error)
    TextView error;
    @InjectView(R.id.main_activity_fab)
    FloatingActionButton fab;
    private MainPresenter presenter;
    static final int ADD_POST_ACTIVITY_REQUEST = 1;
    static final int EDIT_POST_ACTIVITY_REQUEST = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        final MainAdapter mainAdapter = new MainAdapter();

        ButterKnife.inject(this);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(mainAdapter);

        // Normally use dagger
        presenter = new MainPresenter(FakeDagger.getPostsDaoInstance(getApplication()));

        presenter.titleObservable()
                .compose(lifecycleMainObservable.<String>bindLifecycle())
                .subscribe(MoreViewActions.setTitle(toolbar));

        presenter.itemsObservable()
                .compose(lifecycleMainObservable.<List<MainPresenter.AdapterItem>>bindLifecycle())
                .subscribe(mainAdapter);

        presenter.progressObservable()
                .compose(lifecycleMainObservable.<Boolean>bindLifecycle())
                .subscribe(ViewActions.setVisibility(progress, View.INVISIBLE));

        presenter.errorObservable()
                .map(ErrorHelper.mapThrowableToStringError())
                .compose(lifecycleMainObservable.<String>bindLifecycle())
                .subscribe(ViewActions.setText(error));

        presenter.openDetailsObservable()
                .compose(lifecycleMainObservable.<MainPresenter.AdapterItem>bindLifecycle())
                .subscribe(startDetailsActivityAction(this));

        presenter.startEditActivityObservable()
                .compose(lifecycleMainObservable.<String>bindLifecycle())
                .subscribe(startEditPostActivityAction(this));

        MoreViewObservables.scroll(recyclerView)
                .filter(LoadMoreHelper.mapToNeedLoadMore(layoutManager, mainAdapter))
                .compose(lifecycleMainObservable.bindLifecycle())
                .subscribe(presenter.loadMoreObserver());

        ViewObservable.clicks(fab)
                .compose(lifecycleMainObservable.bindLifecycle())
                .subscribe(presenter.clickOnFabObserver());

        presenter.startCreatePostActivityObservable()
                .compose(lifecycleMainObservable.bindLifecycle())
                .subscribe(startPostActivityAction(this));

        presenter.choiceMenuObservable()
                .compose(lifecycleMainObservable.<String>bindLifecycle())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        showPopup(s).show();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == EDIT_POST_ACTIVITY_REQUEST || requestCode == ADD_POST_ACTIVITY_REQUEST) {
            if (resultCode == RESULT_OK) {
                Observable.just(new Object())
                        .subscribe(presenter.finishedActivityObserver());
            }
        }
    }

    private AlertDialog showPopup(final String postId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setItems(R.array.items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        presenter.editPostObserver().onNext(postId);
                        break;
                    case 1:
                        presenter.deletePostObserver().onNext(postId);
                        break;


                }
            }
        });
        return builder.create();
    }

    @Nonnull
    private static Action1<MainPresenter.AdapterItem> startDetailsActivityAction(final Activity activity) {
        return new Action1<MainPresenter.AdapterItem>() {
            @Override
            public void call(MainPresenter.AdapterItem adapterItem) {
                //noinspection unchecked
                final Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity)
                        .toBundle();
                ActivityCompat.startActivity(activity,
                        DetailsActivity.getIntent(activity, adapterItem.id()),
                        bundle);
            }
        };
    }
    @Nonnull
    private static Action1<Object> startPostActivityAction(final Activity activity) {
        return new Action1<Object>() {
            @Override
            public void call(Object o) {
                final Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity)
                        .toBundle();
                ActivityCompat.startActivity(activity,
                        CreatePostActivity.getIntent(activity, "id"),
                        bundle);
            }
        };
    }

    @Nonnull
    private Action1<String> startEditPostActivityAction(final Activity activity) {
        return new Action1<String>() {
            @Override
            public void call(String s) {
                final Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity)
                        .toBundle();
                ActivityCompat.startActivityForResult(activity,
                        EditPostActivity.getIntent(activity, s),
                        EDIT_POST_ACTIVITY_REQUEST,
                        bundle);
            }
        };
    }

}
