package rocks.tbog.tblauncher.loader;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.ShortcutQuery;
import android.content.pm.ShortcutInfo;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import rocks.tbog.tblauncher.TBApplication;
import rocks.tbog.tblauncher.TagsHandler;
import rocks.tbog.tblauncher.db.DBHelper;
import rocks.tbog.tblauncher.db.ShortcutRecord;
import rocks.tbog.tblauncher.entry.ShortcutEntry;

public class LoadShortcutsEntryItem extends LoadEntryItem<ShortcutEntry> {

    private final TagsHandler tagsHandler;
    private final LauncherApps mLauncherApps;

    public LoadShortcutsEntryItem(Context context) {
        super(context);
        tagsHandler = TBApplication.tagsHandler(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mLauncherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        } else {
            mLauncherApps = null;
        }
    }

    @NonNull
    @Override
    public String getScheme() {
        return ShortcutEntry.SCHEME;
    }

    @Override
    protected ArrayList<ShortcutEntry> doInBackground(Void... arg0) {
        Context ctx = context.get();
        if (ctx == null) {
            return new ArrayList<>();
        }

        List<ShortcutRecord> records = DBHelper.getShortcutsNoIcons(ctx);
        ArrayList<ShortcutEntry> pojos = new ArrayList<>(records.size());

        HashMap<String, ShortcutRecord> oreoMap = new HashMap<>();

        for (ShortcutRecord shortcutRecord : records) {
            if (shortcutRecord.isOreo()) {
                oreoMap.put(shortcutRecord.infoData, shortcutRecord);
                continue;
            }
            String id = ShortcutEntry.generateShortcutId(shortcutRecord.dbId, shortcutRecord.infoData);

            ShortcutEntry pojo = new ShortcutEntry(id, shortcutRecord.dbId, shortcutRecord.packageName, shortcutRecord.infoData);

            pojo.setName(shortcutRecord.displayName);
            pojo.setTags(tagsHandler.getTags(pojo.id));

            pojos.add(pojo);
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            List<ShortcutInfo> shortcutInfos = null;

            ShortcutQuery q = new ShortcutQuery();
            q.setQueryFlags(ShortcutQuery.FLAG_MATCH_PINNED);

            if (mLauncherApps.hasShortcutHostPermission())
                shortcutInfos = mLauncherApps.getShortcuts(q, Process.myUserHandle());
            if (shortcutInfos == null)
                shortcutInfos = Collections.emptyList();

            for (ShortcutInfo shortcutInfo : shortcutInfos) {
                ShortcutRecord record = oreoMap.get(shortcutInfo.getId());
                long dbId = 0;
                String name = null;
                if (record != null) {
                    dbId = record.dbId;
                    name = record.displayName;
                }
                ShortcutEntry pojo = new ShortcutEntry(dbId, shortcutInfo);
                pojo.setName(name);
                pojo.setTags(tagsHandler.getTags(pojo.id));

                pojos.add(pojo);
            }
        }

        return pojos;
    }

}
