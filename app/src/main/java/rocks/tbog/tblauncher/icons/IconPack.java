package rocks.tbog.tblauncher.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

import rocks.tbog.tblauncher.utils.UserHandleCompat;

public interface IconPack<DrawableInfo> {

    @NonNull
    String getPackPackageName();

    void load(PackageManager packageManager);

    @Nullable
    Drawable getComponentDrawable(@NonNull Context ctx, @NonNull ComponentName componentName, @NonNull UserHandleCompat userHandle);

    @NonNull
    Drawable applyBackgroundAndMask(@NonNull Context ctx, @NonNull Drawable defaultBitmap, boolean fitInside);

    @NonNull
    Collection<DrawableInfo> getDrawableList();

    @Nullable
    Drawable getDrawable(@NonNull DrawableInfo drawableInfo);
}
