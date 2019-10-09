package kr.jclab.simplejavasoft.simpleclusterconfig.modulemanager;

import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ModuleManager {
    private File moduleRootDir = null;
    private Map<String, ModuleItem> moduleMap = null;
    private Map<String, List<ModuleItem>> resourceFindMap = null;
    private Method addURLMethod = null;

    private static class Holder {
        private final static ModuleManager INSTANCE = new ModuleManager();
    }

    public static ModuleManager getInstance() {
        return Holder.INSTANCE;
    }

    public void start(File moduleRootDir) {
        try {
            ModuleSearcher searcher = new ModuleSearcher(moduleRootDir);
            searcher.search();
            this.moduleRootDir = moduleRootDir;
            this.moduleMap = searcher.getModuleMap();
            this.resourceFindMap = searcher.getResourceFindMap();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        try {
            this.addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
            this.addURLMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> Class<T> loadModule(String className, ClassLoader classLoader) throws ClassNotFoundException {
        URLClassLoader urlClassLoader;
        if(classLoader != null) {
            urlClassLoader = (URLClassLoader)classLoader;
        }else{
            urlClassLoader = (URLClassLoader)URLClassLoader.getSystemClassLoader();
        }
        try {
            if(classLoader != null) {
                return (Class<T>)Class.forName(className, true, classLoader);
            }else{
                return (Class<T>)Class.forName(className);
            }
        } catch (ClassNotFoundException e) {
            ModuleLoadContext moduleLoadContext = new ModuleLoadContext(classLoader, urlClassLoader);
            return moduleLoadContext.loadClass(className);
        }
    }

    public <T> Class<T> loadModule(String className) throws ClassNotFoundException {
        return this.loadModule(className, null);
    }

    private class ModuleSearcher {
        private final File moduleRootDir;
        private final DocumentBuilder documentBuilder;
        private final Map<String, ModuleItem> moduleMap = new HashMap<>();
        private final Map<String, ArrayList<ModuleItem>> resourceFindMap = new HashMap<>();

        public ModuleSearcher(File moduleRootDir) throws ParserConfigurationException {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            this.moduleRootDir = moduleRootDir;
            this.documentBuilder = factory.newDocumentBuilder();
        }

        private void retrieveModuleDir(File dirForFind) {
            for(File file : dirForFind.listFiles()) {
                if(file.isDirectory()) {
                    retrieveModuleDir(file);
                }else if(file.isFile()){
                    if("module.xml".equalsIgnoreCase(file.getName())) {
                        try {
                            ModuleItem item = new ModuleItem(this.documentBuilder, file);
                            this.moduleMap.put(item.getName(), item);
                            for(ModuleItem.ResourceItem resource : item.getResources()) {
                                for(String path : resource.getIndexes()) {
                                    ArrayList<ModuleItem> list = resourceFindMap.get(path);
                                    if(list == null) {
                                        list = new ArrayList<>();
                                        resourceFindMap.put(path, list);
                                    }
                                    list.add(item);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (SAXException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        void search() {
            retrieveModuleDir(this.moduleRootDir);
        }

        public File getModuleRootDir() {
            return moduleRootDir;
        }

        public Map<String, ModuleItem> getModuleMap() {
            return Collections.unmodifiableMap(this.moduleMap);
        }

        public Map<String, List<ModuleItem>> getResourceFindMap() {
            Map<String, List<ModuleItem>> listMap = new HashMap<>();
            for(Map.Entry<String, ArrayList<ModuleItem>> entry : this.resourceFindMap.entrySet()) {
                listMap.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
            return Collections.unmodifiableMap(listMap);
        }
    }

    private class ModuleLoadContext {
        private final ClassLoader classLoader;
        private final URLClassLoader urlClassLoader;

        private final Set<ModuleItem> loadedModules = new HashSet<>();

        public ModuleLoadContext(ClassLoader classLoader, URLClassLoader urlClassLoader) {
            this.classLoader = classLoader;
            this.urlClassLoader = urlClassLoader;
        }

        private void loadResource(File path, boolean useThrow) {
            try {
                addURLMethod.invoke(this.urlClassLoader, path.toURI().toURL());
            } catch (MalformedURLException e) {
                if(useThrow)
                    throw new RuntimeException(e);
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                if(useThrow)
                    throw new RuntimeException(e);
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                if(useThrow)
                    throw new RuntimeException(e);
                e.printStackTrace();
            }
        }

        private void loadDependencyModules(ModuleItem moduleItem) {
            for(ModuleItem.DependencyModule dependencyModule : moduleItem.getDependencies()) {
                ModuleItem depMod = moduleMap.get(dependencyModule.getName());
                if(!loadedModules.contains(depMod)) {
                    loadedModules.add(depMod);
                    if (depMod != null) {
                        for (ModuleItem.ResourceItem resourceItem : depMod.getResources()) {
                            loadResource(resourceItem.getPath(), false);
                        }
                    }
                }
            }
        }

        public <T> Class<T> loadClass(String className) throws ClassNotFoundException {
            String[] classNameSplit = className.split("\\.");
            StringBuilder packagePath = new StringBuilder();
            for(int i=0, l=classNameSplit.length - 1; i<l; i++) {
                if(packagePath.length() > 0)
                    packagePath.append("/");
                packagePath.append(classNameSplit[i]);
            }
            List<ModuleItem> moduleItems = resourceFindMap.get(packagePath.toString());
            if(moduleItems != null) {
                for (ModuleItem moduleItem : moduleItems) {
                    loadDependencyModules(moduleItem);
                    if (!loadedModules.contains(moduleItem)) {
                        for (ModuleItem.ResourceItem resourceItem : moduleItem.getResources()) {
                            loadResource(resourceItem.getPath(), false);
                        }
                    }
                }
            }else{
                System.err.println("[WARN or ERR] Class<" + className + "> could not find in jboss module path: " + moduleRootDir.getAbsolutePath());
            }
            if(this.classLoader != null) {
                return (Class<T>)Class.forName(className, true, this.classLoader);
            }else{
                return (Class<T>)Class.forName(className);
            }
        }
    }
}
