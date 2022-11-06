package io.github.lgp547.anydoorplugin.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Key;
import com.intellij.util.keyFMap.KeyFMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;

public class ImportUtil {

    public static UnaryOperator<String> anyDoorJarPath = version -> "/io/github/lgp547/any-door/" + version + "/any-door-" + version + ".jar";

    public static void fillAnyDoorJar(Project project, String runModuleName, String version) {
        Module module = getMainModule(project, runModuleName);
        fillJar(project, module, "any-door", version, anyDoorJarPath.apply(version));
    }

    /**
     * @param project    当前运行的项目
     * @param module     main函数所在的模块
     * @param jarName    导入名
     * @param jarVersion 导入版本
     * @param jarPath    完整路径
     */
    public static void fillJar(Project project, Module module, String jarName, String jarVersion, String jarPath) {
        try {
            // 若本地已经导入了jar包就直接去掉
            removeModuleLibrary(module, jarName);

            // 进行导入
            File localRepository = MavenProjectsManager.getInstance(project).getLocalRepository();
            String localPath = localRepository.getPath() + jarPath;
            File file = new File(localPath);
            if (!file.isFile()) {
                String httpPath = "https://s01.oss.sonatype.org/content/repositories/releases" + jarPath;
                FileUtils.copyURLToFile(new URL(httpPath), file);
                if (!file.isFile()) {
                    throw new RuntimeException("get jar file fail");
                }
            }
            ModuleRootModificationUtil.addModuleLibrary(module, jarName + "-" + jarVersion, List.of("file://" + file.getPath()), List.of());
            NotifierUtil.notifyInfo(project, module.getName() + " fill ModuleLibrary success " + file.getPath());
        } catch (Exception e) {
            NotifierUtil.notifyError(project, "fill ModuleLibrary fail: " + e.getMessage());
        }

    }

    @SuppressWarnings("rawtypes")
    public static Module getMainModule(Project project, String runModuleName) {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (StringUtils.isNotBlank(runModuleName)) {
            for (Module module : modules) {
                if (module.getName().equals(runModuleName)) {
                    return module;
                }
            }
        }
        // todo: 目前这里的搜索效果不好，需要进行优化
        List<Module> modulesList = new ArrayList<>();
        for (Module module : modules) {
            if (module instanceof ModuleImpl) {
                KeyFMap keyFMap = ((ModuleImpl) module).get();
                if (null != keyFMap) {
                    Key[] keys = keyFMap.getKeys();
                    for (Key key : keys) {
                        if (key.toString().contains("org.jetbrains.android.AndroidResolveScopeEnlarger.resolveScopeWithTests")) {
                            modulesList.add(module);
                            break;
                        }
                    }
                }
            }
        }
        if (modulesList.size() == 1) {
            return modulesList.get(0);
        }
        throw new RuntimeException("MainModule could not find. size " + modulesList.size());
    }

    public static void removeModuleLibrary(@NotNull Module module, String jarName) {
        ModuleRootModificationUtil.updateModel(module, modifiableRootModel -> {
            LibraryTable moduleLibraryTable = modifiableRootModel.getModuleLibraryTable();
            Iterator<Library> libraryIterator = moduleLibraryTable.getLibraryIterator();
            while (libraryIterator.hasNext()) {
                Library next = libraryIterator.next();
                if (StringUtils.contains(next.getName(), jarName)) {
                    moduleLibraryTable.removeLibrary(next);
                }
            }
        });
    }
}
