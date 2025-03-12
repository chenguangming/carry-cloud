package com.photons.carrycloud.localfile.objects;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import com.photons.carrycloud.utils.Utils;

public class EditableFileAbstraction {

    public enum Scheme {
        CONTENT,
        FILE
    }

    public final Uri uri;
    public final String name;
    public final Scheme scheme;
    public String path;

    public EditableFileAbstraction(@NonNull Context context, @NonNull Uri uri) {
        switch (uri.getScheme()) {
            case ContentResolver.SCHEME_CONTENT:
                this.uri = uri;
                this.scheme = Scheme.CONTENT;

                String tempName = null;
                Cursor c = context.getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME},
                        null,
                        null,
                        null);

                if (c != null) {
                    c.moveToFirst();
                    try {
                        /*
                        The result and whether [Cursor.getString()] throws an exception when the column
                        value is null or the column type is not a string type is implementation-defined.
                        */
                        tempName = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    } catch (Exception e) {
                        tempName = null;
                    }
                    c.close();
                }

                if (tempName == null) {
                    // At least we have something to show the user...
                    tempName = uri.getLastPathSegment();
                }

                this.name = tempName;

                break;
            case ContentResolver.SCHEME_FILE:
                this.scheme = Scheme.FILE;

                String path = uri.getPath();
                if (path == null)
                    throw new NullPointerException("Uri '" + uri + "' is not hierarchical!");
                this.path = Utils.sanitizeInput(path);
                this.name = uri.getLastPathSegment();
                this.uri = null;
                break;
            default:
                throw new IllegalArgumentException(
                        "The scheme '" + uri.getScheme() + "' cannot be processed!");
        }
    }
}
