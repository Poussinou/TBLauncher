package rocks.tbog.tblauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rocks.tbog.tblauncher.db.DBHelper;
import rocks.tbog.tblauncher.entry.AppEntry;
import rocks.tbog.tblauncher.entry.EntryItem;
import rocks.tbog.tblauncher.entry.EntryWithTags;
import rocks.tbog.tblauncher.utils.UserHandleCompat;
import rocks.tbog.tblauncher.utils.Utilities;

public class TagsHandler {
    private final TBApplication mApplication;
    // HashMap with EntryItem id as key and an ArrayList of tags for each
    private final HashMap<String, List<String>> mTagsCache = new HashMap<>();

    TagsHandler(TBApplication application) {
        mApplication = application;
        loadFromDB();
    }

    public void loadFromDB() {
        final HashMap<String, List<String>> tags = new HashMap<>();
        Utilities.runAsync(() -> {
            Map<String, List<String>> dbTags = DBHelper.loadTags(getContext());
            tags.clear();
            tags.putAll(dbTags);
        }, () -> {
            mTagsCache.clear();
            mTagsCache.putAll(tags);
            addDefaultAliases();
        });
    }

    private Context getContext() {
        return mApplication;
    }

    public void addTag(EntryItem entry, String tag) {
        // add to db
        DBHelper.addTag(getContext(), tag, entry);
        // add to cache
        List<String> tags = mTagsCache.get(entry.id);
        if (tags == null)
            mTagsCache.put(entry.id, tags = new ArrayList<>());
        tags.add(tag);
    }

    public void removeTag(EntryItem entry, String tag) {
        // remove from DB
        DBHelper.removeTag(getContext(), tag, entry);
        // remove from cache
        List<String> tags = mTagsCache.get(entry.id);
        if (tags == null)
            return;
        tags.remove(tag);
    }

    public List<String> getTags(String id) {
        List<String> tags = mTagsCache.get(id);
        if (tags == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(tags);
    }

//    public String[] getAllTagsAsArray() {
//        Set<String> tags = getAllTagsAsSet();
//        return tags.toArray(new String[0]);
//    }

    public Set<String> getAllTagsAsSet() {
        Set<String> tags = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : mTagsCache.entrySet()) {
            tags.addAll(entry.getValue());
        }
        tags.remove("");
        return tags;
    }

    private void addDefaultAliases() {
        Context context = getContext();
        final PackageManager pm = context.getPackageManager();
        final Resources res = context.getResources();

        // keep all changes here and apply them after we do all the checks
        Map<String, List<String>> pendingTags = new HashMap<>();

        String phoneApp = getApp(pm, Intent.ACTION_DIAL);
        if (phoneApp != null) {
            String phoneAlias = res.getString(R.string.alias_phone);
            addAliasesToEntry(phoneAlias, phoneApp, pendingTags);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            String contactApp = getAppByCategory(pm, Intent.CATEGORY_APP_CONTACTS);
            if (contactApp != null) {
                String contactAlias = res.getString(R.string.alias_contacts);
                addAliasesToEntry(contactAlias, contactApp, pendingTags);
            }

            String browserApp = getAppByCategory(pm, Intent.CATEGORY_APP_BROWSER);
            if (browserApp != null) {
                String webAlias = res.getString(R.string.alias_web);
                addAliasesToEntry(webAlias, browserApp, pendingTags);
            }

            String mailApp = getAppByCategory(pm, Intent.CATEGORY_APP_EMAIL);
            if (mailApp != null) {
                String mailAlias = res.getString(R.string.alias_mail);
                addAliasesToEntry(mailAlias, mailApp, pendingTags);
            }

            String marketApp = getAppByCategory(pm, Intent.CATEGORY_APP_MARKET);
            if (marketApp != null) {
                String marketAlias = res.getString(R.string.alias_market);
                addAliasesToEntry(marketAlias, marketApp, pendingTags);
            }

            String messagingApp = getAppByCategory(pm, Intent.CATEGORY_APP_MESSAGING);
            if (messagingApp != null) {
                String messagingAlias = res.getString(R.string.alias_messaging);
                addAliasesToEntry(messagingAlias, messagingApp, pendingTags);
            }

            String clockApp = getClockApp(pm);
            if (clockApp != null) {
                String clockAlias = res.getString(R.string.alias_clock);
                addAliasesToEntry(clockAlias, clockApp, pendingTags);
            }
        }

        // apply all pending changes in the cache
        for (Map.Entry<String, List<String>> entry : pendingTags.entrySet()) {
            String entryId = entry.getKey();
            List<String> tags = mTagsCache.get(entryId);
            if (tags == null)
                mTagsCache.put(entryId, tags = new ArrayList<>());
            tags.addAll(entry.getValue());
        }
    }

    private void addAliasesToEntry(String aliases, String entryId, Map<String, List<String>> pendingTags) {
        //add aliases only if they haven't overridden by the user (not in db)
        if (!mTagsCache.containsKey(entryId)) {
            //aliases.replace(",", " ")
            String[] arr = aliases.split(",");
            List<String> tags = pendingTags.get(entryId);
            if (tags == null)
                pendingTags.put(entryId, tags = new ArrayList<>());
            tags.addAll(Arrays.asList(arr));
        }
    }

    private String getApp(PackageManager pm, String action) {
        Intent lookingFor = new Intent(action, null);
        return getApp(pm, lookingFor);
    }

    private String getAppByCategory(PackageManager pm, String category) {
        Intent lookingFor = new Intent(Intent.ACTION_MAIN, null);
        lookingFor.addCategory(category);
        return getApp(pm, lookingFor);
    }

    private String getApp(PackageManager pm, Intent lookingFor) {
        List<ResolveInfo> list = pm.queryIntentActivities(lookingFor, 0);
        if (list.size() == 0) {
            return null;
        } else {
            String packageName = list.get(0).activityInfo.applicationInfo.packageName;
            String className = list.get(0).activityInfo.name;

            UserHandleCompat user = UserHandleCompat.CURRENT_USER;
            return AppEntry.SCHEME + user.getUserComponentName(packageName, className);
        }
    }

    private String getClockApp(PackageManager pm) {
        Intent alarmClockIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

        // Known clock implementations
        // See http://stackoverflow.com/questions/3590955/intent-to-launch-the-clock-application-on-android
        String[][] clockImpls = {
                // Nexus
                {"com.android.deskclock", "com.android.deskclock.DeskClock"},
                // Samsung
                {"com.sec.android.app.clockpackage", "com.sec.android.app.clockpackage.ClockPackage"},
                // HTC
                {"com.htc.android.worldclock", "com.htc.android.worldclock.WorldClockTabControl"},
                // Standard Android
                {"com.android.deskclock", "com.android.deskclock.AlarmClock"},
                // New Android versions
                {"com.google.android.deskclock", "com.android.deskclock.AlarmClock"},
                // Froyo
                {"com.google.android.deskclock", "com.android.deskclock.DeskClock"},
                // Motorola
                {"com.motorola.blur.alarmclock", "com.motorola.blur.alarmclock.AlarmClock"},
                // Sony
                {"com.sonyericsson.organizer", "com.sonyericsson.organizer.Organizer_WorldClock"},
                // ASUS Tablets
                {"com.asus.deskclock", "com.asus.deskclock.DeskClock"}
        };

        UserHandleCompat user = UserHandleCompat.CURRENT_USER;
        for (String[] clockImpl : clockImpls) {
            String packageName = clockImpl[0];
            String className = clockImpl[1];
            try {
                ComponentName cn = new ComponentName(packageName, className);

                pm.getActivityInfo(cn, PackageManager.GET_META_DATA);
                alarmClockIntent.setComponent(cn);

                return AppEntry.SCHEME + user.getUserComponentName(cn);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Try next suggestion, this one does not exists on the phone.
            }
        }

        return null;
    }

    public void setTags(EntryWithTags entry, Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            ArrayList<String> tagsToRemove = new ArrayList<>(getTags(entry.id));
            for (String tag : tagsToRemove)
                removeTag(entry, tag);
        } else {
            List<String> oldTags = DBHelper.loadTags(getContext(), entry.id);

            // tags that need to be removed
            {
                ArrayList<String> tagsToRemove = new ArrayList<>();
                for (String tag : oldTags)
                    if (!tags.contains(tag))
                        tagsToRemove.add(tag);
                for (String tag : tagsToRemove)
                    removeTag(entry, tag);
            }

            // add new tags
            for (String tag : tags) {
                if (oldTags.contains(tag))
                    continue;
                addTag(entry, tag);
            }
        }
        entry.setTags(getTags(entry.id));
    }
}
