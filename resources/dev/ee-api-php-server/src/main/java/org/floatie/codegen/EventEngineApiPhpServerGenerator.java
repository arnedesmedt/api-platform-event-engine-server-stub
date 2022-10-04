package org.floatie.codegen;

import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.AbstractPhpCodegen;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.utils.*;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.servers.*;
import io.swagger.v3.oas.models.media.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;

public class EventEngineApiPhpServerGenerator extends AbstractPhpCodegen implements CodegenConfig {
    public static final String GENERATOR_NAME = "ee-api-php-server";
    public static final String AGGREGATE = "aggregate";
    public static final String EVENTS = "events";
    public static final String CONTROLLER = "controller";

    private Boolean aggregate = false;
    private Boolean events = false;
    private Boolean controller = false;
    private String[] queryMethods = {"GET", "OPTIONS", "HEAD"};

    public EventEngineApiPhpServerGenerator() {
        super();

        apiTemplateFiles = new HashMap<>();
        apiTestTemplateFiles = new HashMap<>();
        apiDocTemplateFiles = new HashMap<>();
        modelDocTemplateFiles = new HashMap<>();
        modelTestTemplateFiles = new HashMap<>();

        apiTemplateFiles.put("message.mustache", ".php");
        supportingFiles.add(new SupportingFile("composer.mustache", "", "composer.json"));
        supportingFiles.add(new SupportingFile("grumphp.mustache", "", "grumphp.yml"));
        supportingFiles.add(new SupportingFile("phpstan.mustache", "", "phpstan.neon"));
        supportingFiles.add(new SupportingFile("phpcs.mustache", "", "phpcs.xml"));

        // Set template dir to ee-api-php-server
        this.setTemplateDir(EventEngineApiPhpServerGenerator.GENERATOR_NAME);
        this.embeddedTemplateDir = this.templateDir();

        this.setSrcBasePath("src");
        this.setParameterNamingConvention("camelCase");
    }

    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(AGGREGATE)) {
            this.aggregate = true;
            apiTemplateFiles = new HashMap<>();
            apiTemplateFiles.put("aggregate.mustache", ".php");
        }

        if (additionalProperties.containsKey(EVENTS)) {
            this.events = true;
            apiTemplateFiles = new HashMap<>();
            apiTemplateFiles.put("event.mustache", ".php");
        }

        if (additionalProperties.containsKey(CONTROLLER)) {
            this.controller = true;
            apiTemplateFiles = new HashMap<>();
            apiTemplateFiles.put("controllerResolver.mustache", ".php");
        }
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return EventEngineApiPhpServerGenerator.GENERATOR_NAME;
    }

    @Override
    public String toGetter(String name) {
        return name;
    }

    // Rewrite the api names
    @Override
    public String toApiImport(String name) {
        int lastIndex = name.lastIndexOf("/");

        if (lastIndex == -1) {
            return apiPackage() + "\\" + name;
        }

        return apiPackage() + "\\" + name.replace("/", "\\").substring(0, name.lastIndexOf("/"));
    }

    @Override
    public String sanitizeTag(String tag) {
        return tag;
    }

    @Override
    public String toApiName(String name) {
        return name;
    }

    @Override
    public String toApiVarName(String name) {
        int lastIndex = name.lastIndexOf("/");
        if (lastIndex == -1) {
            return name;
        }

        return name.substring(lastIndex + 1);
    }

    // Rewrite the model names
    @Override
    public String toModelFilename(String name) {
        return super.toModelFilename(name).replace("BACKSLASH", "/");
    }

    @Override
    @SuppressWarnings("static-method")
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        objs = super.postProcessAllModels(objs);

        Map<String, ModelsMap> objsWithNewModelName = new HashMap<>();
        for (Map.Entry<String, ModelsMap> entry : objs.entrySet()) {
            CodegenModel model = ModelUtils.getModelByName(entry.getKey(), objs);
            Map<String, Object> vendorExtensions = model.getVendorExtensions();
            Object xAggregate = vendorExtensions.get("x-aggregate");
            String aggregate = xAggregate != null ? xAggregate + "_BACKSLASH_Model_BACKSLASH_" : "";
            Object xFolder = vendorExtensions.get("x-folder");
            String folder = xFolder != null ? xFolder + "_BACKSLASH_" : "";
            objsWithNewModelName.put(aggregate + folder + entry.getKey(), entry.getValue());
        }

        return objsWithNewModelName;
    }

    @Override
    public CodegenProperty fromProperty(String name, Schema p) {
        CodegenProperty property = super.fromProperty(name, p);

        Map<String, Object> vendorExtensions = property.getVendorExtensions();
        if (vendorExtensions == null || property.getComplexType() == null || property.getComplexType().indexOf("\\") != -1) {
            return property;
        }

        Object xAggregate = vendorExtensions.get("x-aggregate");
        String aggregate = xAggregate != null ? xAggregate + "\\Model\\" : "";
        Object xFolder = vendorExtensions.get("x-folder");
        String folder = xFolder != null ? xFolder + "\\" : "";
        property.setComplexType("\\" + modelPackage + "\\" + aggregate + folder + property.getComplexType());

        return property;
    }

    // Set vendor extensions to know if it's command or query
    @Override
    public CodegenOperation fromOperation(String path,
                                          String httpMethod,
                                          Operation operation,
                                          List<Server> servers) {
        CodegenOperation codegenOperation = super.fromOperation(path, httpMethod, operation, servers);
        Map<String, Object> vendorExtensions = codegenOperation.vendorExtensions;
        Boolean isQuery = this.isQuery(codegenOperation.httpMethod);
        Boolean isController = vendorExtensions != null ? Boolean.TRUE.equals(vendorExtensions.get("x-controller")) : false;
        codegenOperation.vendorExtensions.put("isQuery", isQuery);
        codegenOperation.vendorExtensions.put("isControllerCommand", ! isQuery && isController);
        codegenOperation.vendorExtensions.put("isAggregateCommand", ! isQuery && ! isController);

        return codegenOperation;
    }

    public Boolean isQuery(String method) {
        return Arrays.stream(this.queryMethods).anyMatch(method.toUpperCase()::equals);
    }

    // Rewrite the tags to get files per operation
    @Override
    public void preprocessOpenAPI(OpenAPI openAPI) {
        Paths paths = openAPI.getPaths();

        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            PathItem path = entry.getValue();

            path.setGet(this.tagFromOperationId(path.getGet(), "Get"));
            path.setHead(this.tagFromOperationId(path.getHead(), "Head"));
            path.setPut(this.tagFromOperationId(path.getPut(), "Put"));
            path.setPost(this.tagFromOperationId(path.getPost(), "Post"));
            path.setDelete(this.tagFromOperationId(path.getDelete(), "Delete"));
            path.setPatch(this.tagFromOperationId(path.getPatch(), "Patch"));
            path.setOptions(this.tagFromOperationId(path.getOptions(), "Options"));
            path.setTrace(this.tagFromOperationId(path.getTrace(), "Trace"));
        }

        super.preprocessOpenAPI(openAPI);
    }

    public Operation tagFromOperationId(Operation operation, String method) {
        if (operation == null) {
            return operation;
        }

        Map<String, Object> extensions = operation.getExtensions();

        if (extensions == null) {
            return null;
        }

        Object aggregate = extensions.get("x-aggregate");
        final String aggregatePrefix = aggregate != null ? aggregate + "/" : "";

        Object xFolder = extensions.get("x-folder");
        final String folder = xFolder != null ? xFolder + "/" : "";

        final Boolean isController = Boolean.TRUE.equals(extensions.get("x-controller"));
        final Boolean isQuery = this.isQuery(method);

        if (this.aggregate) {
            if (aggregate == null || isController) {
                return null;
            }

            ArrayList list = new ArrayList();
            list.add(aggregatePrefix + aggregate);
            operation.setTags(list);
            return operation;
        }

        if (this.events) {
            Object eventsObject = extensions.get("x-aggregate-events");

            if (isQuery || eventsObject == null) {
                return null;
            }

            ArrayList<String> events = (ArrayList<String>) eventsObject;
            events.replaceAll(e -> aggregatePrefix + "Event/" + folder + StringUtils.camelize(e));
            operation.setTags(events);
            return operation;
        }

        String type = null;
        if (this.controller) {
            if (! isQuery && ! isController) {
                return null;
            }

            type = isQuery ? "Resolver/" : "Controller/";
        } else {
            type = isQuery ? "Query/" : "Command/";
        }

        ArrayList list = new ArrayList();
        list.add(aggregatePrefix + type + folder + StringUtils.camelize(operation.getOperationId()));
        operation.setTags(list);

        return operation;
    }
}