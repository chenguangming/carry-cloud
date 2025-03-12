/*
 * Copyright (C) 2014-2020 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.photons.carrycloud.localfile.write;

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import com.photons.carrycloud.App;
import com.photons.carrycloud.localfile.objects.EditableFileAbstraction;
import com.photons.carrycloud.utils.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.Callable;

import kotlin.Unit;

public class WriteTextFileCallable implements Callable<Unit> {
    private final WeakReference<Context> context;
    private final ContentResolver contentResolver;
    private final EditableFileAbstraction fileAbstraction;
    private final File cachedFile;
    private final boolean isRootExplorer;
    private final String dataToSave;

    public WriteTextFileCallable(
            Context context,
            ContentResolver contentResolver,
            EditableFileAbstraction file,
            String dataToSave,
            File cachedFile,
            boolean isRootExplorer) {
        this.context = new WeakReference<>(context);
        this.contentResolver = contentResolver;
        this.fileAbstraction = file;
        this.cachedFile = cachedFile;
        this.dataToSave = dataToSave;
        this.isRootExplorer = isRootExplorer;
    }

    @WorkerThread
    @Override
    public Unit call()
            throws IOException,
            IllegalArgumentException {
        OutputStream outputStream;
        File destFile = null;

        switch (fileAbstraction.scheme) {
            case CONTENT:
                Objects.requireNonNull(fileAbstraction.uri);
                if (fileAbstraction.uri.getAuthority().equals(context.get().getPackageName())) {
                    DocumentFile documentFile =
                            DocumentFile.fromSingleUri(App.instance, fileAbstraction.uri);
                    if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
                        outputStream = contentResolver.openOutputStream(fileAbstraction.uri, "wt");
                    } else {
                        destFile = FileUtils.INSTANCE.fromContentUri(fileAbstraction.uri);
                        outputStream = openFile(destFile, context.get());
                    }
                } else {
                    outputStream = contentResolver.openOutputStream(fileAbstraction.uri, "wt");
                }
                break;
            case FILE:
                File file = new File(fileAbstraction.path);
                Context context = this.context.get();
                if (context == null) {
                    return null;
                }
                outputStream = openFile(file, context);
                destFile = file;
                break;
            default:
                throw new IllegalArgumentException(
                        "The scheme for '" + fileAbstraction.scheme + "' cannot be processed!");
        }

        Objects.requireNonNull(outputStream);

        outputStream.write(dataToSave.getBytes());
        outputStream.close();

        if (cachedFile != null && cachedFile.exists() && destFile != null) {
            // cat cache content to original file and delete cache file
            //ConcatenateFileCommand.INSTANCE.concatenateFile(cachedFile.getPath(), destFile.getPath());
            //cachedFile.delete();
        }
        return Unit.INSTANCE;
    }

    private OutputStream openFile(@NonNull File file, @NonNull Context context)
            throws IOException {
        OutputStream outputStream = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            outputStream = Files.newOutputStream(file.toPath());
        } else {
            outputStream = new FileOutputStream(file);
        }

        if (outputStream == null) {
            throw new FileNotFoundException("Cannot read or write text file!");
        }

        return outputStream;
    }
}
