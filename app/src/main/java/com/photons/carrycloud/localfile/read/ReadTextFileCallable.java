package com.photons.carrycloud.localfile.read;

import android.content.ContentResolver;

import androidx.annotation.WorkerThread;
import androidx.documentfile.provider.DocumentFile;

import com.photons.carrycloud.App;
import com.photons.carrycloud.localfile.objects.EditableFileAbstraction;
import com.photons.carrycloud.localfile.objects.ReturnedValueOnReadFile;
import com.photons.carrycloud.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.Callable;

public class ReadTextFileCallable implements Callable<ReturnedValueOnReadFile> {

  public static final int MAX_FILE_SIZE_CHARS = 50 * 1024;

  private final ContentResolver contentResolver;
  private final EditableFileAbstraction fileAbstraction;
  private final File externalCacheDir;
  private final boolean isRootExplorer;

  private File cachedFile = null;

  public ReadTextFileCallable(
      ContentResolver contentResolver,
      EditableFileAbstraction file,
      File cacheDir,
      boolean isRootExplorer) {
    this.contentResolver = contentResolver;
    this.fileAbstraction = file;
    this.externalCacheDir = cacheDir;
    this.isRootExplorer = isRootExplorer;
  }

  @WorkerThread
  @Override
  public ReturnedValueOnReadFile call()
      throws IOException, OutOfMemoryError {
    InputStream inputStream;

    switch (fileAbstraction.scheme) {
      case CONTENT:
        Objects.requireNonNull(fileAbstraction.uri);

        final App appConfig = App.instance;

        if (fileAbstraction.uri.getAuthority().equals(appConfig.getPackageName())) {
          DocumentFile documentFile = DocumentFile.fromSingleUri(appConfig, fileAbstraction.uri);

          if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
            inputStream = contentResolver.openInputStream(documentFile.getUri());
          } else {
            inputStream = loadFile(FileUtils.INSTANCE.fromContentUri(fileAbstraction.uri));
          }
        } else {
          inputStream = contentResolver.openInputStream(fileAbstraction.uri);
        }
        break;
      case FILE:
//        final HybridFileParcelable hybridFileParcelable = fileAbstraction.hybridFileParcelable;
//        Objects.requireNonNull(hybridFileParcelable);

        File file = new File(fileAbstraction.path);
        inputStream = loadFile(file);

        break;
      default:
        throw new IllegalArgumentException(
            "The scheme for '" + fileAbstraction.scheme + "' cannot be processed!");
    }

    Objects.requireNonNull(inputStream);

    InputStreamReader inputStreamReader = new InputStreamReader(inputStream);

    char[] buffer = new char[MAX_FILE_SIZE_CHARS];

    final int readChars = inputStreamReader.read(buffer);
    boolean tooLong = -1 != inputStream.read();

    inputStreamReader.close();

    final String fileContents;

    if (readChars == -1) {
      fileContents = "";
    } else {
      fileContents = String.valueOf(buffer, 0, readChars);
    }

    return new ReturnedValueOnReadFile(fileContents, cachedFile, tooLong);
  }

  private InputStream loadFile(File file) throws IOException {
    InputStream inputStream;

    if (!file.canWrite() && isRootExplorer) {
      // try loading stream associated using root
      cachedFile = new File(externalCacheDir, file.getName());
      // Scrap previously cached file if exist
      if (cachedFile.exists()) {
        cachedFile.delete();
      }
      cachedFile.createNewFile();
      cachedFile.deleteOnExit();
      // creating a cache file
//      CopyFilesCommand.INSTANCE.copyFiles(file.getAbsolutePath(), cachedFile.getPath());

      FileUtils.INSTANCE.copyFile(file, cachedFile);

      inputStream = new FileInputStream(cachedFile);
    } else if (file.canRead()) {
      // readable file in filesystem
      try {
        inputStream = new FileInputStream(file.getAbsolutePath());
      } catch (FileNotFoundException e) {
        throw new FileNotFoundException(
            "Unable to open file [" + file.getAbsolutePath() + "] for reading");
      }
    } else {
      throw new IOException("Cannot read or write text file!");
    }

    return inputStream;
  }
}
