/*
 *  MIT License
 *  Copyright (c) 2023 EUP
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package eup.dependency.haven.repository;

import eup.dependency.haven.api.CachedLibrary;
import eup.dependency.haven.callback.DownloadCallback;
import eup.dependency.haven.model.Dependency;
import eup.dependency.haven.model.Pom;
import eup.dependency.haven.resolver.DependencyResolver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;

/**
 * Stores and retrieves the POM and library of a dependency
 *
 * @author EUP
 */
public class LocalStorageFactory implements StorageFactory {

  private DependencyResolver resolver;
  private File cacheDirectory;
  private DownloadCallback downloadCallback;

  @Override
  public File downloadPom(
      Dependency dependency, RemoteRepository remoteRepository, String relativePath) {
    try {
      downloadCallback.info("Fetching POM for " + dependency + " in " + remoteRepository.getName());
      File file = getFile(remoteRepository, relativePath);
      if (file != null && file.exists()) {
        downloadCallback.info(
            "Pom for " + dependency + " found in remote repository " + remoteRepository.getName());
        return file;
      }
    } catch (Exception e) {
      downloadCallback.warning(
          "An error occured! Pom for "
              + dependency
              + " was not found in remote repository "
              + remoteRepository.getName()
              + " ERROR:"
              + e);
    }
    return null;
  }

  @Override
  public File getLibrary(Pom pom) {
    // Safety check for pom and its dependency
    if (pom == null || pom.getDependency() == null) {
      if (downloadCallback != null) {
        downloadCallback.error("Cannot get library for a null POM/Dependency.");
      }
      return null;
    }

    String fileName = pom.getDependency().toString();

    // --- START: AAR/JAR FALLBACK LOGIC ---

    // Step 1: Define paths for both .aar and .jar
    String aarRelativePath =
        DependencyResolver.getPathFromDeclaration(pom.getDependency()) + ".aar";
    String jarRelativePath =
        DependencyResolver.getPathFromDeclaration(pom.getDependency()) + ".jar";

    // Step 2: Try to find the .aar file first (in cache and remote)
    if (downloadCallback != null) {
      downloadCallback.info("Attempting to find " + fileName + " as .aar");
    }
    File libraryFile = findLibraryByPath(pom.getDependency(), aarRelativePath);

    // Step 3: If .aar is not found, try to find the .jar file
    if (libraryFile == null) {
      if (downloadCallback != null) {
        downloadCallback.warning(
            ".aar not found for " + fileName + ". Now attempting to find as .jar");
      }
      libraryFile = findLibraryByPath(pom.getDependency(), jarRelativePath);
    }

    // Step 4: If neither was found, log a final error
    if (libraryFile == null) {
      if (downloadCallback != null) {
        downloadCallback.error(
            "Download failed for: "
                + fileName
                + ". Neither .aar nor .jar was found in any repository.");
      }
    }

    return libraryFile;
    // --- END: AAR/JAR FALLBACK LOGIC ---
  }

  // I've created a new private helper method to avoid duplicating the search logic.
  // This keeps the code clean and uses the structure you already have.
  private File findLibraryByPath(Dependency dependency, String relativePath) {
    // Check cache first
    if (cacheDirectory != null) {
      for (LocalRepository repository : LocalRepository.getRepositories(getCacheDirectory())) {
        try {
          File cachedFile = getCachedFile(repository, relativePath);
          if (cachedFile != null && cachedFile.exists()) {
            return cachedFile;
          }
        } catch (IOException e) {
          // Continue to next repository
        }
      }
    }

    // If not in cache, check remote repositories
    if (resolver != null && resolver.repositories != null) {
      for (RemoteRepository repository : resolver.repositories) {
        try {
          File file = getFile(repository, relativePath);
          if (file != null && file.exists()) {
            if (downloadCallback != null) {
              downloadCallback.info(
                  "Library for "
                      + dependency.toString()
                      + " found in remote repository: "
                      + repository.getName());
            }
            return file;
          }
        } catch (IOException e) {
          // Continue to next repository
        }
      }
    }

    return null; // Not found anywhere
  }

  private File getCachedFile(ArtifactRepository repository, String relativePath)
      throws IOException {
    File rootDirectory = new File(cacheDirectory, repository.getName());
    if (!rootDirectory.exists()) {
      FileUtils.forceMkdirParent(rootDirectory);
    }

    File file = new File(rootDirectory, relativePath);
    // the file is not found on the disk, return null
    if (!file.exists()) {
      return null;
    }
    return file;
  }

  private File getFile(ArtifactRepository repository, String relativePath) throws IOException {
    File file = getCachedFile(repository, relativePath);
    if (file != null && file.exists()) {
      return file;
    }

    return downloadFile(repository, relativePath);
  }

  private File downloadFile(ArtifactRepository repository, String relativePath) {
    String downloadUrl = repository.getUrl() + relativePath;
    try {
      URL url = new URL(downloadUrl);
      if (downloadCallback != null) {
        downloadCallback.info("Fetching " + relativePath + " from " + repository.getName());
      }

      InputStream inputStream = url.openStream();
      if (inputStream != null) {
        if (downloadCallback != null) {
          downloadCallback.info(relativePath + " downloaded");
        }
        return save(repository, relativePath, inputStream);
      }
    } catch (IOException e) {
      if (downloadCallback != null) {
        downloadCallback.error(relativePath + " was not found at " + repository.getName());
      }
    }
    return null;
  }

  public File save(ArtifactRepository repository, String path, InputStream inputStream)
      throws IOException {
    File rootDirectory = new File(cacheDirectory, repository.getName());

    if (!rootDirectory.exists()) {
      FileUtils.forceMkdir(rootDirectory);
    }

    File file = new File(rootDirectory, path);
    FileUtils.forceMkdirParent(file);

    if (!file.exists() && !file.createNewFile()) {
      throw new IOException("Failed to create file.");
    }

    FileUtils.copyInputStreamToFile(inputStream, file);
    return file;
  }

  @Override
  public void setCacheDirectory(File directory) {
    if (directory.isFile()) {
      throw new IllegalArgumentException("Cache directory must be a folder");
    }
    if (!directory.canRead() && !directory.canWrite()) {
      throw new IllegalArgumentException("Cache directory must be accessible");
    }
    this.cacheDirectory = directory;
  }

  @Override
  public File getCacheDirectory() {
    return this.cacheDirectory;
  }

  @Override
  public void attach(DependencyResolver resolver) {
    if (resolver == null) {
      throw new IllegalArgumentException("DependencyResolver has not been attached");
    }
    this.resolver = resolver;
  }

  @Override
  public void downloadLibraries(Pom pom) {
    List<Dependency> resolvedDependencies = pom.getDependencies();
    if (resolvedDependencies == null || resolvedDependencies.isEmpty()) {
      // If there's nothing to download, call done with an empty list.
      if (downloadCallback != null) {
        downloadCallback.done(new ArrayList<>());
      }
      return;
    }

    List<CachedLibrary> cachedLibraryList = new ArrayList<>();
    for (Dependency dependency : resolvedDependencies) {

      // ADDED: Safety check to prevent NullPointerException
      if (dependency == null) {
        continue; // Ignore this null entry and proceed to the next dependency
      }

      File library = getLibrary(new Pom(dependency));
      // track all cached libraries
      if (library != null) {
        CachedLibrary cachedLibrary = new CachedLibrary();
        cachedLibrary.setSourcePath(library.getAbsolutePath());
        // add the dependency coordinates to pom
        Pom cachedPom = new Pom(dependency.getCoordinates());
        cachedLibrary.setLibraryPom(cachedPom);
        cachedLibraryList.add(cachedLibrary);
      }
    }

    if (downloadCallback != null) {
      downloadCallback.done(cachedLibraryList);
    }
  }

  @Override
  public void setDownloadCallback(DownloadCallback callback) {
    if (callback == null) {
      throw new IllegalArgumentException("DownloadCallback has not been set");
    }
    this.downloadCallback = callback;
  }
}
