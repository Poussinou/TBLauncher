package rocks.tbog.tblauncher.dataprovider;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rocks.tbog.tblauncher.BuildConfig;
import rocks.tbog.tblauncher.DataHandler;
import rocks.tbog.tblauncher.TBApplication;
import rocks.tbog.tblauncher.TBLauncherActivity;
import rocks.tbog.tblauncher.db.FavRecord;
import rocks.tbog.tblauncher.entry.EntryItem;
import rocks.tbog.tblauncher.searcher.Searcher;

public class FavProvider implements IProvider {
    final Context context;
    /**
     * Storage for search items used by this provider
     */
    List<EntryItem> favList = new ArrayList<>();
    List<EntryItem> quickList = new ArrayList<>();
    private boolean mIsLoaded = false;
    private FavLoader mLoadTask = null;
    private long start;

    public FavProvider(Context context) {
        this.context = context;
    }

    @Override
    public void requestResults(String s, Searcher searcher) {

    }

    @Override
    public void reload(boolean cancelCurrentLoadTask) {
        if (!cancelCurrentLoadTask && mLoadTask != null)
            return;
        if (mLoadTask != null)
            mLoadTask.cancel(true);
        Log.i(Provider.TAG, "Starting provider: " + this.getClass().getSimpleName());
        start = System.currentTimeMillis();
        mLoadTask = new FavLoader(this);
        mLoadTask.execute();
    }

    @Override
    public boolean isLoaded() {
        return mIsLoaded;
    }

    @Override
    public void setDirty() {
        // mark this as not loaded and wait for DataHandler to call reload
        mIsLoaded = false;
        if (mLoadTask != null)
            mLoadTask.cancel(true);
        mLoadTask = null;
    }

    @Override
    public int getLoadStep() {
        return LOAD_STEP_2;
    }

    @Override
    public boolean mayFindById(@NonNull String id) {
        return false;
    }

    @Override
    public EntryItem findById(@NonNull String id) {
        return null;
    }

    @Override
    public List<? extends EntryItem> getPojos() {
        if (BuildConfig.DEBUG)
            return Collections.unmodifiableList(favList);
        return favList;
    }

    @NonNull
    public List<? extends EntryItem> getQuickList() {
        return quickList;
    }

    private static class FavLoader extends AsyncTask<Void, Void, Object[]> {
        private final WeakReference<FavProvider> weakProvider;

        public FavLoader(FavProvider provider) {
            weakProvider = new WeakReference<>(provider);
        }

        @Nullable
        private Context getContext() {
            FavProvider provider = weakProvider.get();
            return provider != null ? provider.context : null;
        }

        @Override
        protected Object[] doInBackground(Void... voids) {
            Context ctx = getContext();
            if (ctx == null)
                return null;
            DataHandler dataHandler = TBApplication.getApplication(ctx).getDataHandler();
            ArrayList<FavRecord> list = dataHandler.getFavorites();
            ArrayList<EntryItem> favList = new ArrayList<>(list.size());
            ArrayList<EntryItem> quickList = new ArrayList<>(list.size());
            // get EntryItem from FavRecord
            for (FavRecord fav : list) {
                EntryItem entry = dataHandler.getPojo(fav.record);
                if (entry == null)
                    continue;
                favList.add(entry);
                if (fav.isInQuickList())
                    quickList.add(entry);
            }

            quickList.trimToSize();

            Object[] result = new Object[2];
            result[0] = favList;
            result[1] = quickList;
            return result;
        }

        @Override
        protected void onPostExecute(Object[] entryItems) {
            FavProvider provider = weakProvider.get();
            if (entryItems == null || provider == null || provider.mLoadTask != this)
                return;
            @SuppressWarnings("unchecked")
            List<EntryItem> favList = (List<EntryItem>) entryItems[0];
            @SuppressWarnings("unchecked")
            List<EntryItem> quickList = (List<EntryItem>) entryItems[1];

            // get the result
            provider.favList = favList;
            provider.quickList = quickList;

            // mark the provider as loaded
            provider.mIsLoaded = true;
            provider.mLoadTask = null;

            {
                long time = System.currentTimeMillis() - provider.start;
                Log.i("Provider", "Time to load " + provider.getClass().getSimpleName() + ": " + time + "ms");
                Intent i = new Intent(TBLauncherActivity.LOAD_OVER);
                provider.context.sendBroadcast(i);
            }

            TBApplication.quickList(provider.context).onFavoritesChanged();
        }
    }
}
