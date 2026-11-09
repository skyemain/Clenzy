package com.skyemain.clenzylite;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class Gallery {
    private Gallery() {}

    static String[] permissions() {
        if (Build.VERSION.SDK_INT >= 34) return new String[] {Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED};
        if (Build.VERSION.SDK_INT >= 33) return new String[] {Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        return new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    static int access(Context context) {
        if (Build.VERSION.SDK_INT < 33) return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) ? 3 : 0;
        int access = (granted(context, Manifest.permission.READ_MEDIA_IMAGES) ? 1 : 0)
            | (granted(context, Manifest.permission.READ_MEDIA_VIDEO) ? 2 : 0);
        if (Build.VERSION.SDK_INT >= 34 && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) access |= 4;
        return access;
    }

    private static boolean granted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    static List<ReviewSession.Item> load(Context context, int access) {
        var items = new ArrayList<ReviewSession.Item>();
        var resolver = context.getContentResolver();
        for (String volume : MediaStore.getExternalVolumeNames(context)) {
            for (boolean video : new boolean[] {false, true}) {
                if ((access & (video ? 6 : 5)) == 0) continue;
                Uri collection = video ? MediaStore.Video.Media.getContentUri(volume) : MediaStore.Images.Media.getContentUri(volume);
                try (var cursor = resolver.query(collection, new String[] {"_id", "_display_name", "date_modified", "_size"},
                        "is_pending=0 AND is_trashed=0", null, null)) {
                    if (cursor == null) continue;
                    while (cursor.moveToNext()) items.add(new ReviewSession.Item(
                        ContentUris.withAppendedId(collection, cursor.getLong(0)).toString(),
                        cursor.isNull(1) ? (video ? "Video" : "Photo") : cursor.getString(1),
                        video, cursor.getLong(2), cursor.getLong(3)));
                }
            }
        }
        items.sort(Comparator.comparingLong(ReviewSession.Item::modified).reversed().thenComparing(ReviewSession.Item::uri));
        return items;
    }

    static Bitmap preview(ContentResolver resolver, ReviewSession.Item item) throws IOException {
        Uri uri = Uri.parse(item.uri());
        if (item.video()) return resolver.loadThumbnail(uri, new Size(1080, 1080), null);
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
            int width = info.getSize().getWidth(), height = info.getSize().getHeight();
            float scale = Math.min(1f, 1800f / Math.max(width, height));
            decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
        });
    }

    static Boolean trashState(ContentResolver resolver, ReviewSession.Item item, boolean checkFreshness) {
        Bundle options = new Bundle();
        options.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
        try (var cursor = resolver.query(Uri.parse(item.uri()),
                new String[] {"is_trashed", "_display_name", "date_modified", "_size"}, options, null)) {
            if (cursor == null || !cursor.moveToFirst()) return null;
            if (checkFreshness && (!item.title().equals(cursor.getString(1))
                    || item.modified() != cursor.getLong(2) || item.size() != cursor.getLong(3))) return null;
            return cursor.getInt(0) != 0;
        } catch (RuntimeException error) { return null; }
    }
}
