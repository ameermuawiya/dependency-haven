package eup.dependency.haven.resolver;

import eup.dependency.haven.async.AsyncTaskExecutor;
import eup.dependency.haven.callback.DependencyResolutionCallback;
import eup.dependency.haven.model.Coordinates;
import eup.dependency.haven.model.Dependency;
import eup.dependency.haven.model.Pom;
import eup.dependency.haven.parser.PomParser;
import eup.dependency.haven.repository.LocalRepository;
import eup.dependency.haven.repository.RemoteRepository;
import eup.dependency.haven.repository.Repository;
import eup.dependency.haven.repository.StorageFactory;
import eup.dependency.haven.resolver.internal.DependencyResolutionSkipper;
import eup.dependency.haven.versioning.ComparableVersion;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.xml.sax.SAXException;

@SuppressWarnings("unused")
public class DependencyResolver implements Repository {

  private Coordinates coordinates;

  // Add these two new member variables at the top of your class
  private volatile boolean isCancelled = false;
  private ExecutorService executorService;
  // data structure for bfs transversal
  private Queue<Dependency> queue = new LinkedList<>();
  private Set<Dependency> seen = new HashSet<>();
  // keep track of all resolved dependencies
  private List<Dependency> resolvedDependencies = new LinkedList<>();
  // keep track of all unresolved dependencies
  private List<Dependency> unresolvedDependencies = new ArrayList<>();
  private DependencyResolutionCallback callback;
  // storage factory for caching resolved artifacts
  private StorageFactory storageFactory;
  private DependencyResolutionSkipper skipper;
  public final List<RemoteRepository> repositories;
  private boolean skipInnerDependencies = false;

  // TODO: REWORK THIS CLASS TO USE POM INSTEAD OF DEPENDEBCY WHILE ITERATING
  // SO THAT I CLOUD PRIORITIZE HIGHER VERSIONS OF POMS USING MAP WITH OREFERENCE TO HIGHER POM
  // VERSIONS

  /**
   * Creates a DependencyResolver
   *
   * @param coordinates The dependency coordinates to resolve for
   */
  public DependencyResolver(Coordinates coordinates) {
    this(null, coordinates);
  }

  /**
   * Creates a DependencyResolver with a factory
   *
   * @param storageFactory the factory for managing cached dependencies and POMs
   * @param coordinates The dependency coordinates to resolve for
   */
  public DependencyResolver(StorageFactory storageFactory, Coordinates coordinates) {
    this.storageFactory = storageFactory;
    this.coordinates = coordinates;
    this.repositories = new ArrayList<>();
  }

  public void resolve(DependencyResolutionCallback callback) {
    this.isCancelled = false;
    this.callback = callback;
    this.skipper = new DependencyResolutionSkipper(callback);

    if (callback == null) {
        throw new IllegalArgumentException("Dependency Resolution Callback must be set.");
    }

    if (coordinates == null || coordinates.toString().isEmpty()) {
        callback.warning("Please enter a dependency declaration.");
        return;
    }

    this.resolvedDependencies.clear();
    this.unresolvedDependencies.clear();
    this.seen.clear();
    this.queue.clear();

    try {
        long startTime = System.currentTimeMillis();
        callback.info("Starting Resolution for " + coordinates);

        AsyncTaskExecutor.loadTaskAsync(
            () -> {
                Dependency rootDependency = new Dependency(coordinates);
                
                if (skipInnerDependencies) {
                    InputStream pomStream = searchRepositories(rootDependency);
                    if (pomStream == null) {
                        unresolvedDependencies.add(rootDependency);
                        return null; // Failure
                    }
                    try {
                        Pom parsedPom = resolvePom(pomStream);
                        String packaging = parsedPom.getCoordinates().getPackaging();
                        if (packaging != null && !packaging.isEmpty()) {
                            rootDependency.setType(packaging);
                        }
                        resolvedDependencies.add(rootDependency);
                    } finally {
                        pomStream.close();
                    }
                    return resolvedDependencies;
                }

                // --- Start: Full Transitive Dependency Resolution (BFS) ---
                queue.add(rootDependency);
                seen.add(rootDependency);

                while (!queue.isEmpty()) {
                    if (isCancelled) break;

                    Dependency currentDependency = queue.poll();
                    callback.info("Resolving: " + currentDependency);
                    
                    InputStream pomStream = searchRepositories(currentDependency);
                    if (pomStream == null) {
                        callback.warning("Could not find POM for: " + currentDependency);
                        unresolvedDependencies.add(currentDependency);
                        continue; // Skip to next dependency in queue
                    }

                    try {
                        Pom parsedPom = resolvePom(pomStream);
                        
                        // Set packaging type if not already set
                        if (currentDependency.getType() == null || currentDependency.getType().isEmpty()) {
                            String packaging = parsedPom.getCoordinates().getPackaging();
                            if (packaging != null && !packaging.isEmpty()) {
                                currentDependency.setType(packaging);
                            }
                        }
                        
                        // Add the successfully resolved dependency to the final list
                        if (!resolvedDependencies.contains(currentDependency)) {
                            resolvedDependencies.add(currentDependency);
                        }
                        
                        // Add its children to the queue
                        for (Dependency child : parsedPom.getDependencies()) {
                            if (!seen.contains(child)) {
                                seen.add(child);
                                queue.add(child);
                            }
                        }
                    } finally {
                        pomStream.close();
                    }
                }
                // --- End: Full Transitive Dependency Resolution (BFS) ---

                if (isCancelled) return Collections.emptyList();
                
                if (resolvedDependencies.isEmpty()) {
                    return null; // Indicate failure
                }

                return resolvedDependencies;
            },
            (List<Dependency> resultDependencies) -> {
                long endTime = System.currentTimeMillis();

                if (isCancelled) {
                    callback.warning("Resolution was cancelled by the user.");
                    return;
                }
                
                if (resultDependencies == null || resultDependencies.isEmpty()) {
                    String finalError = "Could not resolve: " + coordinates + ".\n" + "Please check the library name, version, and your internet connection.";
                    callback.onDependencyNotResolved(finalError, unresolvedDependencies);
                } else {
                    callback.onDependenciesResolved("Successfully resolved " + coordinates, resultDependencies, (endTime - startTime));
                }
            });
    } catch (Exception e) {
        callback.error("Failed to resolve " + coordinates + ": " + e.getMessage());
    }
}

  /**
   * Resolves a dependency and adds its direct and transitive to list
   *
   * @param coordinates the dependency to resolve for
   */
  private List<Dependency> resolveDependencies(Dependency dependency) {
    if (skipInnerDependencies) {
      List<Dependency> directDependencies = new ArrayList<>();
      InputStream is = null;
      Dependency directDependency = dependency;
      try {
        is = searchRepositories(dependency);
        Pom parsedPom = new Pom();
        // parse the pom
        parsedPom = resolvePom(is);
        // retrieve the packaging of the direct dependency
        directDependency.setType(parsedPom.getCoordinates().getPackaging());
        if (is == null) {
          callback.error(
              "Failed to resolve " + dependency + ",Cause: search repositories was null");
          return Collections.emptyList();
        }
      } catch (IOException e) {
        callback.error("Failed to retrieve info for " + dependency + " " + e.getMessage());
      } finally {
        try {
          is.close();
        } catch (IOException e) {
          callback.error("Failed to close search " + e.getMessage());
        }
      }
      directDependencies.add(directDependency);
      return directDependencies;
    } else {
      resolve(new Pom(dependency));
      return resolvedDependencies;
    }
  }

  /**
   * Sets the dependency type from it's packaging
   *
   * <p>If the type is not declared in a POM the packaging is also aliased as type
   *
   * @param dependency the dependency with an ambiguous type
   */
  private String fallbackType(Dependency dependency) {
    String defaultType = "jar";

    try {
      InputStream is = searchRepositories(dependency);
      if (is == null) {
        return defaultType;
      }
      Pom parsedPom = new Pom();
      // parse the pom
      parsedPom = resolvePom(is);
      is.close();
      return parsedPom.getCoordinates().getPackaging();
    } catch (Exception e) {
      callback.error("Failed to get extension for " + dependency + " with ambiguous type " + e);
      // ignore and default type to jar
      return defaultType;
    }
  }

  /**
   * Resolves a dependency from POM
   *
   * @param pom the POM of a dependency
   */
  private void resolve(Pom pom) {
    Dependency parent = pom.getDependency();

    InputStream inputStream = searchRepositories(parent);
    if (inputStream == null) {
      return;
    }
    Pom parsePom = new Pom();
    try {
      // parse the pom
      parsePom = resolvePom(inputStream);

      parsePom
          .getDependencies()
          .forEach(
              directDependency -> {
                // Check if type is not explicitly declared
                if (directDependency.getType() == null || directDependency.getType().isEmpty()) {
                  directDependency.setType(fallbackType(directDependency));
                }
                // add each dependency to the search
                queue.add(directDependency);
              });

    } catch (Exception e) {
      callback.error("Failed to resolve " + parent + " " + e.getMessage());
    }

    while (!queue.isEmpty()) {

      if (isCancelled) {
        callback.warning("Resolution was cancelled.");
        return; // Stop the loop immediately
      }

      // pull a dependency to resolve
      Dependency currentDependency = queue.poll();

      Pom currPom = new Pom();
      // add just pulled dependency as coordinates to pom
      currPom.setCoordinates(currentDependency.getCoordinates());
      currPom.setExclusions(parsePom.getExclusions());

      if (skipper.skipResolution(seen, currentDependency, currPom)) {
        unresolvedDependencies.add(currentDependency);
        continue;
      }

      seen.forEach(
          visitedDependency -> {
            if (skipper.hasVersionConflicts(visitedDependency, currentDependency)) {
              String msg =
                  "Version conflict detected for "
                      + visitedDependency.toString()
                      + " against "
                      + currentDependency.toString()
                      + " conflicting version would be resolved as configured";
              callback.warning(msg);
              // TODO: Handle version conflicts
            }
          });

      if (!seen.contains(parent)) {
        callback.info("Resolving inner dependency: " + currentDependency);
      }

      if (!seen.contains(currentDependency)) {
        seen.add(currentDependency);
        // add the unseen dependency to pom
        currPom.addDependency(currentDependency);
        // also add unseen dependency to pom
        currPom.setCoordinates(currentDependency.getCoordinates());
        resolvedDependencies.add(currentDependency);
        callback.info("Successfully resolved " + currentDependency);
      }

      try {
        // Add the adjacent dependencies to the BFS queue.
        for (Dependency adjacent : currPom.getDependencies()) {
          if (!seen.contains(adjacent)) {
            queue.add(adjacent);
          }
          // TODO: Only resolve transitive dependencies of POMS with higher version
          // if that exist..data struct Map
          // resolve the transitive dependencies for each adjacent dependency coordinate
          resolveTransitiveDependencies(adjacent, resolvedDependencies);
        }
      } catch (Exception e) {
        callback.warning("Failed to resolve " + currentDependency + " " + e.getMessage());
      }
    }
    try {
      inputStream.close();
    } catch (IOException e) {
      callback.error("Failed to close input stream " + inputStream + " " + e.getMessage());
    }
  }

  public void skipInnerDependencies(boolean enabled) {
    this.skipInnerDependencies = enabled;
  }

  /**
   * Applies DFS (Depth First Search) to recrusively transverse a tree in order to all trace
   * transitive dependencies
   *
   * @param coordinates dependency coordinate to resolve for
   * @param resolvedDependencies the direct dependencies
   */
  private void resolveTransitiveDependencies(
      Dependency indirectDependency, List<Dependency> resolvedDependencies)
      throws IOException, SAXException {
    if (resolvedDependencies.isEmpty()) {
      return;
    }
    for (Dependency transitiveDependency : resolveDependencies(indirectDependency)) {
      // prevent unnecessary recursion
      if (!resolvedDependencies.contains(transitiveDependency)) {
        resolveTransitiveDependencies(transitiveDependency, resolvedDependencies);
      }
    }
  }

  @Override
  public Pom getParentPom(Coordinates coordinates) {
    InputStream is = null;
    try {
      is = searchRepositories(new Dependency(coordinates));
      Pom parsedPom = new Pom();
      callback.info("Parsing parent POM " + coordinates);
      parsedPom = resolvePom(is);
      return parsedPom;
    } catch (IOException e) {
      callback.error("Failed to parse parent POM for " + coordinates + " " + e.getMessage());
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        callback.error("Failed to close search for POM" + e.getMessage());
      }
    }
    return null;
  }

  /**
   * Resolves a POM to get declared pom information
   *
   * @param is the POM input stream
   * @throws IOException in case of I/O error
   * @throws SAXException in case of sax error
   */
  private Pom resolvePom(InputStream is) throws IOException {
    PomParser parser = new PomParser(this);
    return parser.parse(is);
  }

  /**
   * Prioritises the higher version of a dependency coordinate
   *
   * @param firstVersion the first version to compare
   * @param secondVersion the second version to compare
   */
  private int getHigherVersion(String firstVersion, String secondVersion) {
    ComparableVersion firstComparableVersion = new ComparableVersion(firstVersion);
    ComparableVersion secondComparableVersion = new ComparableVersion(secondVersion);
    return firstComparableVersion.compareTo(secondComparableVersion);
  }

  public InputStream searchRepositories(Dependency dependency) {
    if (dependency == null) {
      return null;
    }
    String pomPath = getPomDownloadURL(dependency);

    for (RemoteRepository repository : repositories) {
      if (isCancelled) {
        return null;
      }

      File pomFile = storageFactory.downloadPom(dependency, repository, pomPath);

      if (pomFile != null && pomFile.exists()) {
        try {
          if (callback != null) {
            callback.info("Found " + dependency.getCoordinates() + " in " + repository.getName());
          }
          return new FileInputStream(pomFile);
        } catch (java.io.FileNotFoundException e) {
          if (callback != null) {
            callback.error("Found POM file but failed to open it: " + e.getMessage());
          }
        }
      }
    }
    return null;
  }

  /**
   * Gets a library download url for a dependency
   *
   * <p>The url can also be used with local repositories that follow same remote declaration path
   *
   * @param dependency the dependency to provide library url for
   */
  public static String getLibraryDownloadURL(Dependency dependency) {
    if (dependency == null) {
      return "";
    }
    return getPathFromDeclaration(dependency)
        + ("aar".equalsIgnoreCase(dependency.getType())
            ? ".aar"
            : ".jar" /*+ dependency.getType()*/); // was dependency.getType() but bundle e.t.c could
    // also mean .jar
  }

  /**
   * Gets a POM download url for a dependency
   *
   * <p>The url can also be used with local repositories that follow same remote declaration path
   *
   * @param dependency the dependency to provide POM url for
   */
  public static String getPomDownloadURL(Dependency dependency) {
    if (dependency == null) {
      return "";
    }
    return getPathFromDeclaration(dependency) + ".pom";
  }

  /**
   * Gets a declaration path for a dependency from it's coordinates
   *
   * <p>For example: {@code io.eup:test:1.5} we get a path io/eup/test/1.5/test-1.5
   */
  public static String getPathFromDeclaration(Dependency dependency) {
    if (dependency == null) {
      return "";
    }
    return dependency.getCoordinates().getGroupId().replace(".", "/")
        + "/"
        + dependency.getCoordinates().getArtifactId()
        + "/"
        + dependency.getCoordinates().getVersion()
        + "/"
        + dependency.getCoordinates().getArtifactId()
        + "-"
        + dependency.getCoordinates().getVersion();
  }

  @Override
  public void addRepository(RemoteRepository repository) {
    repositories.add(repository);
  }

  @Override
  public void addRepository(String name, String url) {
    addRepository(new RemoteRepository(name, url));
  }

  /** Cancels all ongoing resolution and download tasks. */
  public void cancel() {
    this.isCancelled = true;
    if (this.executorService != null && !this.executorService.isShutdown()) {
      // This will interrupt all background network tasks immediately.
      this.executorService.shutdownNow();
    }
  }
}
