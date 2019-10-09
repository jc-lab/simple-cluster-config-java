package kr.jclab.simplejavasoft.simpleclusterconfig.modulemanager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleItem {
    private final String name;
    private final List<ResourceItem> resources;
    private final List<DependencyModule> dependencies;

    public ModuleItem(DocumentBuilder documentBuilder, File moduleFile) throws IOException, SAXException {
        ArrayList<DependencyModule> dependencyModules = new ArrayList<>();
        ArrayList<ResourceItem> resourceModules = new ArrayList<>();

        Document document = documentBuilder.parse(moduleFile);
        Element root = document.getDocumentElement();
        this.name = root.getAttribute("name");

        {
            NodeList resourcesNodes = root.getElementsByTagName("resources");
            if (resourcesNodes.getLength() > 0) {
                Node node = resourcesNodes.item(0);
                if (node instanceof Element) {
                    NodeList resourceRootNodes = ((Element) node).getElementsByTagName("resource-root");
                    resourceModules.ensureCapacity(resourceRootNodes.getLength());
                    for (int i = 0, l = resourceRootNodes.getLength(); i < l; i++) {
                        Node resRootNode = resourceRootNodes.item(i);
                        if(resRootNode instanceof Element) {
                            String resPath = ((Element)resRootNode).getAttribute("path");
                            resourceModules.add(new ResourceItem(new File(moduleFile.getParentFile(), resPath)));
                        }
                    }
                }
            }
        }
        {
            NodeList nodes = root.getElementsByTagName("dependencies");
            if (nodes.getLength() > 0) {
                Node dependenciesNode = nodes.item(0);
                if (dependenciesNode instanceof Element) {
                    NodeList moduleNodes = ((Element) dependenciesNode).getElementsByTagName("module");
                    dependencyModules.ensureCapacity(moduleNodes.getLength());
                    for (int i = 0, l = moduleNodes.getLength(); i < l; i++) {
                        Node moduleNode = moduleNodes.item(i);
                        if(moduleNode instanceof Element) {
                            DependencyModule dependencyModule = new DependencyModule(((Element)moduleNode).getAttribute("name"));
                            if(dependencyModule.isValid()) {
                                dependencyModules.add(dependencyModule);
                            }
                        }
                    }
                }
            }
        }

        this.resources = Collections.unmodifiableList(resourceModules);
        this.dependencies = Collections.unmodifiableList(dependencyModules);
    }

    public String getName() {
        return name;
    }

    public List<ResourceItem> getResources() {
        return resources;
    }

    public List<DependencyModule> getDependencies() {
        return dependencies;
    }

    public static class DependencyModule {
        private final String name;
        private final boolean valid;

        public DependencyModule(String name) {
            this.name = name;

            this.valid = (name != null);
        }

        public boolean isValid() {
            return this.valid;
        }

        public String getName() {
            return name;
        }
    }

    public static class ResourceItem {
        private final File path;
        private final boolean valid;
        private final List<String> indexes;

        public ResourceItem(File path) {
            boolean valid = false;
            ArrayList<String> indexes = new ArrayList<>();
            File indexFile = new File(path.getParentFile(), path.getName() + ".index");
            this.path = path;

            do {
                if (!indexFile.isFile()) {
                    break;
                }

                try(InputStream inputStream = new FileInputStream(indexFile)) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while((line = reader.readLine()) != null) {
                        indexes.add(line.trim());
                    }

                }catch (IOException e){
                    e.printStackTrace();
                    break;
                }

                valid = true;
            } while(false);

            this.valid = valid;
            this.indexes = indexes;
        }

        public File getPath() {
            return path;
        }

        public List<String> getIndexes() {
            return indexes;
        }

        public boolean isValid() {
            return valid;
        }
    }
}