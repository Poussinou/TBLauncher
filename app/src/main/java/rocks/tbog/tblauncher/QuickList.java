package rocks.tbog.tblauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.PaintDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import java.util.List;

import rocks.tbog.tblauncher.dataprovider.FavProvider;
import rocks.tbog.tblauncher.dataprovider.Provider;
import rocks.tbog.tblauncher.entry.EntryItem;
import rocks.tbog.tblauncher.utils.UIColors;

public class QuickList {
    private TBLauncherActivity mTBLauncherActivity;
    private boolean mIsEnabled = true;
    private boolean mAlwaysVisible = true;
    private LinearLayout mQuickList;

    // bAdapterEmpty is true when no search results are displayed
    private boolean bAdapterEmpty = true;

    // is any filter activated?
    private boolean bFilterOn = false;

    // last filter scheme, used for better toggle behaviour
    private String mLastFilter = null;

    public Context getContext() {
        return mTBLauncherActivity;
    }

    public void onCreateActivity(TBLauncherActivity tbLauncherActivity) {
        mTBLauncherActivity = tbLauncherActivity;

        mQuickList = mTBLauncherActivity.findViewById(R.id.quickList);
        populateList();
    }

    public void onFavoritesChanged() {
        if (mQuickList == null)
            return;
        populateList();
    }

    private void populateList() {
        mQuickList.removeAllViews();
        if (!isQuickListEnabled()) {
            mQuickList.setVisibility(View.GONE);
            return;
        }
        FavProvider provider = TBApplication.getApplication(getContext()).getDataHandler().getFavProvider();
        List<? extends EntryItem> list = provider != null ? provider.getQuickList() : null;
        if (list == null)
            return;
        for (EntryItem entry : list) {
            View view = LayoutInflater.from(getContext()).inflate(entry.getResultLayout(), mQuickList, false);
            entry.displayResult(view);
            mQuickList.addView(view);
        }
        mQuickList.setVisibility(mQuickList.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    public void toggleFilter(View v, Provider<? extends EntryItem> provider) {
        Context ctx = v.getContext();
        TBApplication app = TBApplication.getApplication(ctx);

        // if there is no search we need to filter, just show all matching entries
        if (bAdapterEmpty) {
            if (bFilterOn && provider != null && mLastFilter == provider.getScheme()) {
                app.behaviour().clearAdapter();
                bFilterOn = false;
            } else {
                List<? extends EntryItem> list;
                list = provider != null ? provider.getPojos() : null;
                if (list != null) {
                    app.behaviour().updateAdapter(list, false);
                    mLastFilter = provider.getScheme();
                    bFilterOn = true;
                } else {
                    bFilterOn = false;
                }
            }
            // updateAdapter will change `bAdapterEmpty` and we change it back because we want
            // bAdapterEmpty to represent a search we need to filter
            bAdapterEmpty = true;
        } else if (bFilterOn && (provider == null || mLastFilter == provider.getScheme())) {
            animToggleOff();
            bFilterOn = false;
            app.behaviour().filterResults(null);
        } else if (provider != null) {
            animToggleOff();
            bFilterOn = true;
            mLastFilter = provider.getScheme();
            app.behaviour().filterResults(provider.getScheme());
        }

        // show what is currently toggled
        if (bFilterOn) {
            animToggleOn(v);
        }
    }

    private void animToggleOn(View v) {
        if (v.getTag(R.id.tag_anim) instanceof ValueAnimator) {
            ValueAnimator colorAnim = (ValueAnimator) v.getTag(R.id.tag_anim);
            colorAnim.start();
        } else {
            int colorTo = UIColors.getPrimaryColor(v.getContext());
            int colorFrom = v.getBackground() instanceof ColorDrawable ? ((ColorDrawable) v.getBackground()).getColor() : (colorTo & 0x00FFFFFF);
            ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
            colorAnim.addUpdateListener(animator -> v.setBackgroundColor((int) animator.getAnimatedValue()));
//            colorAnim.addListener(new AnimatorListenerAdapter() {
//                @Override
//                public void onAnimationEnd(Animator animation, boolean isReverse) {
//                    if (isReverse)
//                        mLastFilter = null;
//                }
//            });
            colorAnim.start();
            v.setTag(R.id.tag_anim, colorAnim);
        }
    }

    private void animToggleOff() {
        if (!bFilterOn)
            return;
        int n = mQuickList.getChildCount();
        for (int i = 0; i < n; i += 1) {
            View view = mQuickList.getChildAt(i);
            if (mLastFilter == null || mLastFilter == view.getTag(R.id.tag_filterScheme)) {
                if (view.getTag(R.id.tag_anim) instanceof ValueAnimator) {
                    ValueAnimator colorAnim = (ValueAnimator) view.getTag(R.id.tag_anim);
                    colorAnim.reverse();
                } else {
                    view.setBackgroundColor(UIColors.getPrimaryColor(mQuickList.getContext()) & 0x00FFFFFF);
                }
            }
        }
    }

    private boolean isQuickListEnabled() {
        return mIsEnabled;
    }

    public void showQuickList() {
        if (mAlwaysVisible)
            show();
    }

    private void show() {
        if (isQuickListEnabled()) {
            mQuickList.animate()
                    .scaleY(1f)
                    .setListener(null)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
            mQuickList.setVisibility(View.VISIBLE);
        }
    }

    public void hideQuickList(boolean animate) {
        if (isQuickListEnabled()) {
            animToggleOff();
            if (animate) {
                mQuickList.animate()
                        .scaleY(0f)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mQuickList.setVisibility(View.GONE);
                            }
                        })
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                mQuickList.setVisibility(View.VISIBLE);
            } else {
                mQuickList.setScaleY(0f);
                mQuickList.setVisibility(View.GONE);
            }
        } else {
            mQuickList.setVisibility(View.GONE);
        }
    }

    public void onResume(SharedPreferences pref) {
        Resources resources = mQuickList.getResources();
        mIsEnabled = pref.getBoolean("quick-list-enabled", true);
        mAlwaysVisible = pref.getBoolean("quick-list-always-visible", true);

        // size
        int percent = pref.getInt("quick-list-size", 0);

        // set layout height
        {
            int smallSize = resources.getDimensionPixelSize(R.dimen.bar_height);
            int largeSize = resources.getDimensionPixelSize(R.dimen.large_bar_height);
            ViewGroup.LayoutParams params = mQuickList.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                params.height = smallSize + (largeSize - smallSize) * percent / 100;
                mQuickList.setLayoutParams(params);
            } else {
                throw new IllegalStateException("mSearchBarContainer has the wrong layout params");
            }
        }

        int color = UIColors.getColor(pref, "quick-list-color");
        int alpha = UIColors.getAlpha(pref, "quick-list-alpha");

        // rounded drawable
        PaintDrawable drawable = new PaintDrawable();
        drawable.getPaint().setColor(UIColors.setAlpha(color, alpha));
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mQuickList.getLayoutParams();
        drawable.setCornerRadius(getContext().getResources().getDimension(R.dimen.bar_corner_radius));
        mQuickList.setBackground(drawable);
        int margin = (int) (params.height * .25f);
        params.setMargins(margin, 0, margin, margin);
    }

    public void adapterCleared() {
        animToggleOff();
        bFilterOn = false;
        bAdapterEmpty = true;
        if (!mAlwaysVisible)
            hideQuickList(true);
    }

    public void adapterUpdated() {
        show();
        animToggleOff();
        bFilterOn = false;
        bAdapterEmpty = false;
    }
}
