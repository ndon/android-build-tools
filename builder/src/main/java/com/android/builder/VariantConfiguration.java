/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Variant configuration.
 */
public class VariantConfiguration {

    private final static ManifestParser sManifestParser = new DefaultManifestParser();

    private final ProductFlavor mDefaultConfig;
    private final SourceSet mDefaultSourceSet;

    private final BuildType mBuildType;
    private final SourceSet mBuildTypeSourceSet;

    private final List<ProductFlavor> mFlavorConfigs = new ArrayList<ProductFlavor>();
    private final List<SourceSet> mFlavorSourceSets = new ArrayList<SourceSet>();

    private final Type mType;
    /** Optional tested config in case type is Type#TEST */
    private final VariantConfiguration mTestedConfig;
    /** An optional output that is only valid if the type is Type#LIBRARY so that the test
     * for the library can use the library as if it was a normal dependency. */
    private AndroidDependency mOutput;

    private ProductFlavor mMergedFlavor;

    private List<JarDependency> mJars;

    /** List of direct library project dependencies. Each object defines its own dependencies. */
    private final List<AndroidDependency> mDirectLibraryProjects =
            new ArrayList<AndroidDependency>();
    /** list of all library project dependencies in the flat list.
     * The order is based on the order needed to call aapt: earlier libraries override resources
     * of latter ones. */
    private final List<AndroidDependency> mFlatLibraryProjects = new ArrayList<AndroidDependency>();

    public static enum Type {
        DEFAULT, LIBRARY, TEST;
    }

    /**
     * Creates the configuration with the base source set.
     *
     * This creates a config with a {@link Type#DEFAULT} type.
     *
     * @param defaultConfig
     * @param defaultSourceSet
     * @param buildType
     * @param buildTypeSourceSet
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceSet defaultSourceSet,
            @NonNull BuildType buildType, @NonNull SourceSet buildTypeSourceSet) {
        this(defaultConfig, defaultSourceSet,
                buildType, buildTypeSourceSet,
                Type.DEFAULT, null /*testedConfig*/);
    }

    /**
     * Creates the configuration with the base source set for a given {@link Type}.
     *
     * @param defaultConfig
     * @param defaultSourceSet
     * @param buildType
     * @param buildTypeSourceSet
     * @param type
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceSet defaultSourceSet,
            @NonNull BuildType buildType, @NonNull SourceSet buildTypeSourceSet,
            @NonNull Type type) {
        this(defaultConfig, defaultSourceSet,
                buildType, buildTypeSourceSet,
                type, null /*testedConfig*/);
    }

    /**
     * Creates the configuration with the base source set, and whether it is a library.
     * @param defaultConfig
     * @param defaultSourceSet
     * @param buildType
     * @param buildTypeSourceSet
     * @param type
     * @param testedConfig
     */
    public VariantConfiguration(
            @NonNull ProductFlavor defaultConfig, @NonNull SourceSet defaultSourceSet,
            @NonNull BuildType buildType, @NonNull SourceSet buildTypeSourceSet,
            @NonNull Type type, @Nullable VariantConfiguration testedConfig) {
        mDefaultConfig = defaultConfig;
        mDefaultSourceSet = defaultSourceSet;
        mBuildType = buildType;
        mBuildTypeSourceSet = buildTypeSourceSet;
        mType = type;
        mTestedConfig = testedConfig;

        assert mType != Type.TEST || mTestedConfig != null;
        assert mTestedConfig == null ||
                mTestedConfig.mType != Type.LIBRARY ||
                mTestedConfig.mOutput != null;

        mMergedFlavor = mDefaultConfig;

        validate();
    }

    /**
     * Add a new configured ProductFlavor.
     *
     * If multiple flavors are added, the priority follows the order they are added when it
     * comes to resolving Android resources overlays (ie earlier added flavors supersedes
     * latter added ones).
     *
     * @param sourceSet the configured product flavor
     */
    public void addProductFlavor(@NonNull ProductFlavor productFlavor,
                                 @NonNull SourceSet sourceSet) {
        mFlavorConfigs.add(productFlavor);
        mFlavorSourceSets.add(sourceSet);
        mMergedFlavor = productFlavor.mergeOver(mMergedFlavor);
    }

    public void setJarDependencies(List<JarDependency> jars) {
        mJars = jars;
    }

    /**
     * Set the Library Project dependencies.
     * @param directLibraryProjects list of direct dependencies. Each library object should contain
     *            its own dependencies.
     */
    public void setAndroidDependencies(@NonNull List<AndroidDependency> directLibraryProjects) {
        if (directLibraryProjects != null) {
            mDirectLibraryProjects.addAll(directLibraryProjects);
        }

        resolveIndirectLibraryDependencies(getFullDirectDependencies(), mFlatLibraryProjects);
    }

    /**
     * Returns all direct dependencies, including the tested config if it's a library itself.
     * @return
     */
    public List<AndroidDependency> getFullDirectDependencies() {
        if (mTestedConfig != null && mTestedConfig.getType() == Type.LIBRARY) {
            // in case of a library we merge all the dependencies together.
            List<AndroidDependency> list = new ArrayList<AndroidDependency>(
                    mDirectLibraryProjects.size() +
                            mTestedConfig.mDirectLibraryProjects.size() + 1);
            list.addAll(mDirectLibraryProjects);
            list.add(mTestedConfig.mOutput);
            list.addAll(mTestedConfig.mDirectLibraryProjects);

            return list;
        }

        return mDirectLibraryProjects;
    }

    public String getLibraryPackages() {
        if (mFlatLibraryProjects.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (AndroidDependency dep : mFlatLibraryProjects) {
            File manifest = dep.getManifest();
            String packageName = sManifestParser.getPackage(manifest);
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(packageName);
        }

        return sb.toString();
    }


    public void setOutput(AndroidDependency output) {
        mOutput = output;
    }

    public ProductFlavor getDefaultConfig() {
        return mDefaultConfig;
    }

    public SourceSet getDefaultSourceSet() {
        return mDefaultSourceSet;
    }

    public ProductFlavor getMergedFlavor() {
        return mMergedFlavor;
    }

    public BuildType getBuildType() {
        return mBuildType;
    }

    public SourceSet getBuildTypeSourceSet() {
        return mBuildTypeSourceSet;
    }

    public boolean hasFlavors() {
        return !mFlavorConfigs.isEmpty();
    }

    /**
     * @Deprecated this is only valid until we move to more than one flavor
     */
    @Deprecated
    public ProductFlavor getFirstFlavor() {
        return mFlavorConfigs.get(0);
    }

    /**
     * @Deprecated this is only valid until we move to more than one flavor
     */
    @Deprecated
    public SourceSet getFirstFlavorSourceSet() {
        return mFlavorSourceSets.get(0);
    }

    public Iterable<ProductFlavor> getFlavorConfigs() {
        return mFlavorConfigs;
    }

    public Iterable<SourceSet> getFlavorSourceSets() {
        return mFlavorSourceSets;
    }

    public boolean hasLibraries() {
        return !mDirectLibraryProjects.isEmpty();
    }

    public Iterable<AndroidDependency> getDirectLibraries() {
        return mDirectLibraryProjects;
    }

    public Iterable<AndroidDependency> getFlatLibraries() {
        return mFlatLibraryProjects;
    }

    public Type getType() {
        return mType;
    }

    VariantConfiguration getTestedConfig() {
        return mTestedConfig;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a flat list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param directDependencies the libraries to resolve
     * @param outFlatDependencies where to store all the libraries.
     */
    @VisibleForTesting
    void resolveIndirectLibraryDependencies(List<AndroidDependency> directDependencies,
                                            List<AndroidDependency> outFlatDependencies) {
        if (directDependencies == null) {
            return;
        }
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = directDependencies.size() - 1  ; i >= 0 ; i--) {
            AndroidDependency library = directDependencies.get(i);

            // get its libraries
            List<AndroidDependency> dependencies = library.getDependencies();

            // resolve the dependencies for those libraries
            resolveIndirectLibraryDependencies(dependencies, outFlatDependencies);

            // and add the current one (if needed) in front (higher priority)
            if (outFlatDependencies.contains(library) == false) {
                outFlatDependencies.add(0, library);
            }
        }
    }

    /**
     * Returns the package name for this variant. This could be coming from the manifest or
     * could be overridden through the product flavors.
     * @return the package
     */
    public String getPackageName() {
        String packageName;

        if (mType == Type.TEST) {
            packageName = mMergedFlavor.getTestPackageName();
            if (packageName == null) {
                String testedPackage = mTestedConfig.getPackageName();

                packageName = testedPackage + ".test";
            }
        } else {
            packageName = getPackageOverride();
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }
        }

        return packageName;
    }

    public String getTestedPackageName() {
        if (mType == Type.TEST) {
            if (mTestedConfig.mType == Type.LIBRARY) {
                return getPackageName();
            } else {
                return mTestedConfig.getPackageName();
            }
        }

        return null;
    }

    /**
     * Returns the package override values coming from the Product Flavor. If the package is not
     * overridden then this returns null.
     * @return the package override or null
     */
    public String getPackageOverride() {

        String packageName = mMergedFlavor.getPackageName();
        String packageSuffix = mBuildType.getPackageNameSuffix();

        if (packageSuffix != null && packageSuffix.length() > 0) {
            if (packageName == null) {
                packageName = getPackageFromManifest();
            }

            if (packageSuffix.charAt(0) == '.') {
                packageName = packageName + packageSuffix;
            } else {
                packageName = packageName + '.' + packageSuffix;
            }
        }

        return packageName;
    }

    private final static String DEFAULT_TEST_RUNNER = "android.test.InstrumentationTestRunner";

    public String getInstrumentationRunner() {
        String runner = mMergedFlavor.getTestInstrumentationRunner();
        return runner != null ? runner : DEFAULT_TEST_RUNNER;
    }

    /**
     * Reads the package name from the manifest.
     * @return
     */
    public String getPackageFromManifest() {
        File manifestLocation = mDefaultSourceSet.getAndroidManifest();
        return sManifestParser.getPackage(manifestLocation);
    }

    /**
     * Returns the dynamic list of resource folders based on the configuration, its dependencies,
     * as well as tested config if applicable (test of a library).
     * @return a list of input resource folders.
     */
    public List<File> getResourceInputs() {
        List<File> inputs = new ArrayList<File>();

        if (mBuildTypeSourceSet != null) {
            File typeResLocation = mBuildTypeSourceSet.getAndroidResources();
            if (typeResLocation != null) {
                inputs.add(typeResLocation);
            }
        }

        for (SourceSet sourceSet : mFlavorSourceSets) {
            File flavorResLocation = sourceSet.getAndroidResources();
            if (flavorResLocation != null) {
                inputs.add(flavorResLocation);
            }
        }

        File mainResLocation = mDefaultSourceSet.getAndroidResources();
        if (mainResLocation != null) {
            inputs.add(mainResLocation);
        }

        for (AndroidDependency dependency : mFlatLibraryProjects) {
            File resFolder = dependency.getResFolder();
            if (resFolder != null) {
                inputs.add(resFolder);
            }
        }

        return inputs;
    }

    /**
     * Returns the compile classpath for this config. If the config tests a library, this
     * will include the classpath of the tested config
     * @return
     */
    public Set<File> getCompileClasspath() {
        Set<File> classpath = new HashSet<File>();

        for (File f : mDefaultSourceSet.getCompileClasspath()) {
            classpath.add(f);
        }

        if (mBuildTypeSourceSet != null) {
            for (File f : mBuildTypeSourceSet.getCompileClasspath()) {
                classpath.add(f);
            }
        }

        for (SourceSet sourceSet : mFlavorSourceSets) {
            for (File f : sourceSet.getCompileClasspath()) {
                classpath.add(f);
            }
        }

        if (mType == Type.TEST && mTestedConfig.mType == Type.LIBRARY) {
            // the tested library is added to the main app so we need its compile classpath as well.
            // which starts with its output
            classpath.add(mTestedConfig.mOutput.getJarFile());
            classpath.addAll(mTestedConfig.getCompileClasspath());
        }

        return classpath;
    }


    protected void validate() {
        if (mType != Type.TEST) {
            File manifest = mDefaultSourceSet.getAndroidManifest();
            if (!manifest.isFile()) {
                throw new IllegalArgumentException(
                        "Main Manifest missing from " + manifest.getAbsolutePath());
            }
        }
    }
}