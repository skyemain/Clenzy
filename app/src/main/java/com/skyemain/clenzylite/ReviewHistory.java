package com.skyemain.clenzylite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.HashMap;
import java.util.Map;

final class ReviewHistory extends SQLiteOpenHelper {
    ReviewHistory(Context context) { super(context, "review-order.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reviewed (uri TEXT PRIMARY KEY NOT NULL, rank INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX review_rank ON reviewed(rank)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    Map<String, Long> ranks() {
        Map<String, Long> ranks = new HashMap<>();
        try (var rows = getReadableDatabase().rawQuery("SELECT uri, rank FROM reviewed", null)) {
            while (rows.moveToNext()) ranks.put(rows.getString(0), rows.getLong(1));
        }
        return ranks;
    }

    void mark(String uri) {
        getWritableDatabase().execSQL(
            "INSERT OR REPLACE INTO reviewed(uri, rank) SELECT ?, COALESCE(MAX(rank),0)+1 FROM reviewed",
            new Object[] {uri});
    }
}
