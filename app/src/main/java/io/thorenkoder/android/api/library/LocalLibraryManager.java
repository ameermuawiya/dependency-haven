package io.thorenkoder.android.api.library;

import android.os.Build;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import eup.dependency.haven.api.CachedLibrary;
import eup.dependency.haven.repository.StorageFactory; // FIX: Added the missing import
import io.thorenkoder.android.api.exception.DexFailedException;
import io.thorenkoder.android.util.BaseUtil;
import io.thorenkoder.android.util.SDKUtil;
import io.thorenkoder.android.util.SDKUtil.API;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mod.agus.jcoderz.dx.command.dexer.Main;
import mod.hey.studios.lib.JarCheck;
import org.apache.commons.io.FileUtils;

/**
 * A local library manager for Sketchware Pro.
 *
 * @author EUP
 */
public class LocalLibraryManager {

    public interface TaskListener {
        void info(String message);
        void error(String message);
    }

    private TaskListener listener;
    private File newDirectory;
    private File androidJar;
    private File lambdaStubs;
    private Map<String, File> localLibraryJar = new HashMap<>();

    public LocalLibraryManager(StorageFactory storageFactory, File newDirectory) {
        this.newDirectory = newDirectory;
    }

    public void setCompileResourcesClassPath(File androidJar, File lambdaStubs) {
        this.androidJar = androidJar;
        this.lambdaStubs = lambdaStubs;
    }

    public void setTaskListener(TaskListener listener) {
        this.listener = listener;
    }

    public boolean copyCachedLibrary(List<CachedLibrary> cachedLibraries) {
        if (cachedLibraries == null || cachedLibraries.isEmpty()) {
            return true;
        }

        localLibraryJar.clear();
        boolean allLibrariesProcessedSuccessfully = true;

        for (CachedLibrary cachedLibrary : cachedLibraries) {
            LocalLibrary localLibrary = new LocalLibrary();
            localLibrary.setLibraryName(cachedLibrary.getLibraryPom().getCoordinates().toString());
            localLibrary.setLibraryPom(cachedLibrary.getLibraryPom());
            localLibrary.setSourceFile(cachedLibrary.getSourceFile());

            File finalLibraryDir = new File(newDirectory, localLibrary.getLibraryName());
            localLibrary.setSourcePath(finalLibraryDir);

            try {
                if (finalLibraryDir.exists()) {
                    if (new File(finalLibraryDir, "classes.dex").exists()) {
                         if (listener != null) listener.info("Library " + localLibrary.getLibraryName() + " already processed. Skipping.");
                         continue;
                    }
                    FileUtils.deleteDirectory(finalLibraryDir);
                }
                FileUtils.forceMkdir(finalLibraryDir);

                File sourceFile = cachedLibrary.getSourceFile();
                if (listener != null) listener.info("Processing: " + localLibrary.getLibraryName());

                if (localLibrary.isJar()) {
                    if (listener != null) listener.info("Copying and renaming " + sourceFile.getName());
                    FileUtils.copyFile(sourceFile, new File(finalLibraryDir, "classes.jar"));
                } else if (localLibrary.isAar()) {
                    if (listener != null) listener.info("Decompressing " + sourceFile.getName());
                    BaseUtil.unzip(sourceFile.getAbsolutePath(), finalLibraryDir.getAbsolutePath());
                    File config = new File(finalLibraryDir, "config");
                    FileUtils.write(config, localLibrary.findPackageName(), StandardCharsets.UTF_8);
                    localLibrary.deleteUnnecessaryFiles();
                }

                File classesJar = localLibrary.getJarFile();
                if (classesJar.exists()) {
                    localLibraryJar.put(localLibrary.getLibraryName(), classesJar);
                } else if (localLibrary.isAar()) {
                    if (listener != null) listener.error("Critical error: classes.jar not found in " + localLibrary.getLibraryName());
                    allLibrariesProcessedSuccessfully = false;
                }

            } catch (Exception e) {
                if (listener != null) listener.error("Failed to process " + localLibrary.getLibraryName() + ": " + e.getMessage());
                allLibrariesProcessedSuccessfully = false;
                try {
                    if (finalLibraryDir.exists()) FileUtils.deleteDirectory(finalLibraryDir);
                } catch (IOException ex) { /* Ignore cleanup error */ }
            }
        }

        if (!localLibraryJar.isEmpty()) {
            if (listener != null) listener.info("Starting dexing for " + localLibraryJar.size() + " libraries...");
            for (Map.Entry<String, File> entry : localLibraryJar.entrySet()) {
                try {
                    compileJar(entry.getValue());
                } catch (Exception e) {
                    if (listener != null) listener.error("Dexing failed for " + entry.getKey() + ": " + e.getMessage());
                    allLibrariesProcessedSuccessfully = false;
                }
            }
        }

        return allLibrariesProcessedSuccessfully;
    }

    private void compileJar(File jarFile) throws CompilationFailedException, IOException {
        if (SDKUtil.isAtLeast(API.ANDROID_8)) {
            D8Command.Builder commandBuilder = D8Command.builder()
                .setIntermediate(true)
                .setMode(CompilationMode.RELEASE)
                .addProgramFiles(jarFile.toPath())
                .setOutput(jarFile.getParentFile().toPath(), OutputMode.DexIndexed);

            commandBuilder.setMinApiLevel(Build.VERSION.SDK_INT);

            listener.info("Dexing jar " + jarFile.getParentFile().getName() + " using D8 with API " + Build.VERSION.SDK_INT);
            D8.run(commandBuilder.build());

        } else {
            if (!JarCheck.checkJar(jarFile.getAbsolutePath(), 41, 51)) {
                Main.clearInternTables();
                listener.info("Dexing jar " + jarFile.getParentFile().getName() + " using DX");
                Main.main(new String[] {
                    "--debug", "--verbose", "--multi-dex",
                    "--output=" + jarFile.getParentFile().getAbsolutePath(),
                    jarFile.getAbsolutePath()
                });
            } else {
                throw new DexFailedException(
                    jarFile.getParentFile().getName() +
                    " uses Java >= 1.8 features and cannot be compiled on this device.");
            }
        }
    }
    
    private List<Path> getCompileResources() {
        List<Path> resources = new ArrayList<>();
        if (lambdaStubs != null) resources.add(lambdaStubs.toPath());
        if (androidJar != null) resources.add(androidJar.toPath());
        return resources;
    }
}