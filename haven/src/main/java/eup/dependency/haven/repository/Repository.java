package eup.dependency.haven.repository;

import eup.dependency.haven.model.Coordinates;
import eup.dependency.haven.model.Pom;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public interface Repository {

    Pom getParentPom(Coordinates coordinates);

    void addRepository(RemoteRepository repository) throws IllegalArgumentException;

    void addRepository(String name, String url) throws IllegalArgumentException;

    class Manager {

        // --- START: YOUR MODERNIZED AND CORRECTED LIST ---
        // Your new list is excellent. JCenter is removed and Sonatype is added.
        public static final List<RemoteRepository> DEFAULT_REMOTE_REPOSITORIES =
            Arrays.asList(
                // 1. Google's Maven repository (for AndroidX and Google libraries)
                new RemoteRepository("google", "https://maven.google.com"),
                // 2. Maven Central (the largest repository for Java libraries)
                new RemoteRepository("maven-central", "https://repo1.maven.org/maven2"),
                // 3. JitPack (for libraries hosted on GitHub)
                new RemoteRepository("jitpack", "https://jitpack.io"),
                // 4. Sonatype Snapshots (for development/snapshot versions of libraries)
                new RemoteRepository("sonatype-snapshots", "https://s01.oss.sonatype.org/content/repositories/snapshots")
            );
        // --- END: YOUR MODERNIZED AND CORRECTED LIST ---
        
        public static List<RemoteRepository> readRemoteRepositoryConfig(
            File jsonFile, boolean useDefaultRepos) throws JSONException, IOException {
            if (jsonFile == null || !jsonFile.exists()) {
                return Collections.emptyList();
            }
            BufferedReader br = new BufferedReader(new FileReader(jsonFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim());
            }
            br.close();
            String json = sb.toString();
            return readRemoteRepositoryConfig(json, useDefaultRepos);
        }

        public static List<RemoteRepository> readRemoteRepositoryConfig(
            String jsonString, boolean useDefaultRepos) throws JSONException {
            List<RemoteRepository> repositories = new ArrayList<>();
            if (jsonString == null || jsonString.isEmpty()) {
                if (useDefaultRepos) {
                    repositories.addAll(DEFAULT_REMOTE_REPOSITORIES);
                }
                return repositories;
            }
            JSONArray array = new JSONArray(jsonString);
            for (int i = 0; i < array.length(); i++) {
                JSONObject repo = array.getJSONObject(i);
                repositories.add(new RemoteRepository(repo.getString("name"), repo.getString("url")));
            }

            if (useDefaultRepos) {
                repositories.addAll(DEFAULT_REMOTE_REPOSITORIES);
            }
            return repositories;
        }

        // --- START: CRASH-PROOF METHODS ---
        /**
         * Generates a JSON string from the default list of repositories.
         * This version is now safe against null values in the default list.
         */
        public static String generateJSON() {
            return generateJSON(DEFAULT_REMOTE_REPOSITORIES);
        }

        /**
         * Generates a JSON string from a list of RemoteRepository objects.
         * This version is hardened to prevent crashes from null repositories or null URLs.
         */
        public static String generateJSON(List<RemoteRepository> remoteRepositoryList) {
            if (remoteRepositoryList == null) {
                return "[]"; // Return empty JSON array if the list itself is null
            }
            
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            
            boolean isFirstEntry = true;
            for (RemoteRepository repository : remoteRepositoryList) {
                // Skip any entry that is null or has a null name/URL to prevent crashes.
                if (repository == null || repository.getName() == null || repository.getUrl() == null) {
                    continue; // Move to the next repository
                }

                // Append comma before adding the next element (except for the first one)
                if (!isFirstEntry) {
                    sb.append(",");
                }

                sb.append("{\"name\": \"");
                sb.append(repository.getName());
                sb.append("\", \"url\": \"");

                String url = repository.getUrl();
                if (url.endsWith("/")) {
                    // Remove trailing slash for consistency
                    sb.append(url.substring(0, url.length() - 1));
                } else {
                    sb.append(url);
                }
                sb.append("\"}");
                
                isFirstEntry = false;
            }
            
            sb.append("]");
            return sb.toString();
        }
        // --- END: CRASH-PROOF METHODS ---
    }
}