package io.github.swagger2markup.internal.document;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.swagger2markup.OpenAPI2MarkupConverter;
import io.github.swagger2markup.adoc.ast.impl.BlockImpl;
import io.github.swagger2markup.adoc.ast.impl.ListImpl;
import io.github.swagger2markup.adoc.ast.impl.ListItemImpl;
import io.github.swagger2markup.adoc.ast.impl.SectionImpl;
import io.github.swagger2markup.adoc.ast.impl.TableImpl;
import io.github.swagger2markup.extension.MarkupComponent;
import io.github.swagger2markup.internal.component.ExternalDocumentationComponent;
import io.github.swagger2markup.internal.component.ParametersComponent;
import io.github.swagger2markup.internal.component.ResponseComponent;
import io.github.swagger2markup.internal.component.SecurityRequirementTableComponent;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariables;
import io.swagger.v3.oas.models.tags.Tag;

import static io.github.swagger2markup.adoc.converter.internal.Delimiters.LINE_SEPARATOR;
import static io.github.swagger2markup.config.OpenAPILabels.LABEL_SERVER;
import static io.github.swagger2markup.config.OpenAPILabels.SECTION_TITLE_RESOURCES;
import static io.github.swagger2markup.config.OpenAPILabels.SECTION_TITLE_SERVERS;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_DEFAULT;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_DESCRIPTION;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_POSSIBLE_VALUES;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_VARIABLE;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_TITLE_SERVER_VARIABLES;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.appendDescription;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.appendDescriptionSection;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.italicUnconstrained;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.monospaced;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class PathsDocument extends MarkupComponent<Document, PathsDocument.Parameters, Document> {
    private final ParametersComponent parametersComponent;
    private final ExternalDocumentationComponent externalDocumentationComponent;
    private final ResponseComponent responseComponent;
    private final SecurityRequirementTableComponent securityRequirementTableComponent;

    public PathsDocument(OpenAPI2MarkupConverter.OpenAPIContext context) {
        super(context);
        this.parametersComponent = new ParametersComponent(context);
        this.externalDocumentationComponent = new ExternalDocumentationComponent(context);
        this.responseComponent = new ResponseComponent(context);
        this.securityRequirementTableComponent = new SecurityRequirementTableComponent(context);
    }

    public static Parameters parameters(OpenAPI schema) {
        return new Parameters(schema);
    }

    @Override
    public Document apply(Document document, Parameters parameters) {
        Paths apiPaths = parameters.schema.getPaths();
        List<Tag> tags = parameters.schema.getTags();
        Components components = parameters.schema.getComponents();

        if (null == apiPaths || apiPaths.isEmpty()) return document;

        SectionImpl allPathsSection = new SectionImpl(document);
        allPathsSection.setTitle(labels.getLabel(SECTION_TITLE_RESOURCES));

        Map<String, List<OperationWrapper>> resources = new HashMap<>();
        for (Map.Entry<String, PathItem> path : apiPaths.entrySet()) {
            for (Map.Entry<HttpMethod, Operation> operation : path.getValue().readOperationsMap().entrySet()) {
                for (String tag : operation.getValue().getTags()) {
                    List<OperationWrapper> groupedOperations = new ArrayList<>();
                    if (resources.containsKey(tag)) {
                        groupedOperations = resources.get(tag);
                    }
                    groupedOperations.add(new OperationWrapper(operation.getKey(), path.getKey(), operation.getValue()));
                    resources.put(tag, groupedOperations);
                }
            }
        }

        resources = resources.entrySet()
            .stream()
            .sorted(comparingByKey())
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

        resources.forEach((tagKey, operations) -> {
            SectionImpl tagSection = new SectionImpl(allPathsSection);
            Tag tag = tags.stream().filter(t -> tagKey.equals(t.getName())).findFirst().orElse(null);
            tagSection.setTitle(tag.getName());
            appendDescription(tagSection, tag.getDescription());

            for (OperationWrapper operationWrapper : operations.stream().sorted(Comparator.comparing(o -> Optional.ofNullable(o.getOperation().getSummary()).orElse("?"))).collect(Collectors.toList())) {
                Operation operation = operationWrapper.getOperation();
                SectionImpl operationSection = new SectionImpl(tagSection);
                String summary = Optional.ofNullable(operation.getSummary()).orElse("?");

                operationSection.setTitle(summary.trim());
                appendOperation(operationSection, operationWrapper.getMethod(), operationWrapper.getUrl());
                appendDescriptionSection(operationSection, "Description", operation.getDescription());

                externalDocumentationComponent.apply(operationSection, operation.getExternalDocs());
                appendParameters(operationSection, operation);
                responseComponent.apply(operationSection, operation.getResponses());
                appendMediaTypes(operationSection, operation.getRequestBody(), operation.getResponses());
                appendServersSection(operationSection, operation.getServers());
                securityRequirementTableComponent.apply(operationSection, parameters.schema.getSecurity(), true);
                appendExampleRequest(operationSection, operationWrapper.getUrl(), components, operation.getRequestBody());
                appendExampleResponse(operationSection, components, operation.getResponses());
                tagSection.append(operationSection);
            }

            allPathsSection.append(tagSection);
        });

        document.append(allPathsSection);
        return document;
    }

    private void appendExampleRequest(StructuralNode node, String url, Components components, RequestBody requestBody) {
        SectionImpl exampleRequestSection = new SectionImpl(node);
        exampleRequestSection.setTitle("Example HTTP request");

        SectionImpl requestPathSection = new SectionImpl(exampleRequestSection);
        requestPathSection.setTitle("Request path");
        appendOperation(requestPathSection, null, url);
        exampleRequestSection.append(requestPathSection);

        if (requestBody != null) {
            Optional<Schema> optionalSchema = requestBody.getContent().values().stream().map(MediaType::getSchema).findFirst();
            if (optionalSchema.isPresent()) {
                Schema schema = optionalSchema.get();
                if (!isEmpty(schema.get$ref())) {
                    Schema component = components.getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf("/") + 1));
                    if (component != null) {
                        SectionImpl requestBodySection = new SectionImpl(exampleRequestSection);
                        requestBodySection.setTitle("Request body");
                        appendCodeBlock(requestBodySection, components, component);
                        exampleRequestSection.append(requestBodySection);
                    }
                }
            }
        }

        node.append(exampleRequestSection);
    }
    
    private void appendExampleResponse(StructuralNode node, Components components, ApiResponses responses) {
        SectionImpl exampleRequestSection = new SectionImpl(node);
        exampleRequestSection.setTitle("Example HTTP response");

        Set<Map.Entry<String, ApiResponse>> responseList = responses.entrySet().stream().filter(response -> {
            Integer value = Integer.parseInt(response.getKey());
            return value >= 200 && value < 300;
        }).sorted(Comparator.comparing(Map.Entry::getKey)).collect(Collectors.toCollection(LinkedHashSet::new));

        for (Map.Entry<String, ApiResponse> apiResponse : responseList) {
            Content content = apiResponse.getValue().getContent();
            if (content != null) {
                Optional<Schema> optionalSchema = apiResponse.getValue().getContent().values().stream().map(MediaType::getSchema).findFirst();
                Schema schema = optionalSchema.get();
                if (ArraySchema.class.isAssignableFrom(schema.getClass())) {
                    ArraySchema arraySchema = (ArraySchema) schema;
                    if (!isEmpty(arraySchema.getItems().get$ref())) {
                        Schema component = components.getSchemas().get(arraySchema.getItems().get$ref().substring(arraySchema.getItems().get$ref().lastIndexOf("/") + 1));
                        if (component != null) {
                            SectionImpl responseBodySection = new SectionImpl(exampleRequestSection);
                            responseBodySection.setTitle("Response " + apiResponse.getKey());
                            appendArrayCodeBlock(responseBodySection, components, component);
                            exampleRequestSection.append(responseBodySection);
                        }
                    }
                }
                else if (!isEmpty(schema.get$ref())) {
                    Schema component = components.getSchemas().get(schema.get$ref().substring(schema.get$ref().lastIndexOf("/") + 1));
                    if (component != null) {
                        SectionImpl responseBodySection = new SectionImpl(exampleRequestSection);
                        responseBodySection.setTitle("Response " + apiResponse.getKey());
                        appendCodeBlock(responseBodySection, components, component);
                        exampleRequestSection.append(responseBodySection);
                    }
                }
            }
        }
        node.append(exampleRequestSection);
    }

    private void appendCodeBlock(StructuralNode node, Components components, Schema schema) {
        BlockImpl codeBlock = null;
        try {
            codeBlock = new BlockImpl(node, "source,json", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(generateJsonObject(0, components, schema)));
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        node.append(codeBlock);
    }

    private void appendArrayCodeBlock(StructuralNode node, Components components, Schema schema) {
        BlockImpl codeBlock = null;
        try {
            codeBlock = new BlockImpl(node, "source,json", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(List.of(generateJsonObject(1, components, schema))));
        }
        catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        node.append(codeBlock);
    }

    private Map<String, Object> generateJsonObject(int level, Components components, Schema schema) {
        LinkedHashMap<String, Object> object = new LinkedHashMap<>();
        if (level >= 5) {
            return object;
        }

        Map<String, Schema> properties = schema.getProperties();
        for (Map.Entry<String, Schema> property : properties.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toCollection(LinkedHashSet::new))) {
            String line = property.getKey();
            String ref = property.getValue().get$ref();
            if (!isEmpty(ref)) {
                Map<String, Object> nested = generateJsonObject(level + 1, components, components.getSchemas().get(ref.substring(ref.lastIndexOf("/") + 1)));
                object.put(line, nested);
            }
            else if (ArraySchema.class.isAssignableFrom(property.getValue().getClass())) {
                ArraySchema arraySchema = (ArraySchema) property.getValue();
                ref = arraySchema.getItems().get$ref();
                if (!isEmpty(ref)) {
                    Map<String, Object> nested = generateJsonObject(level + 1, components, components.getSchemas().get(ref.substring(ref.lastIndexOf("/") + 1)));
                    object.put(line, List.of(nested));
                }
                else {
                    generateExamples(object, line, property.getValue());
                }
            }
            else {
                generateExamples(object, line, property.getValue());
            }
        }
        return object;
    }

    public void generateExamples(Map<String, Object> object, String line, Schema property) {
        if (IntegerSchema.class.isAssignableFrom(property.getClass()) || NumberSchema.class.isAssignableFrom(property.getClass())) {
            object.put(line, 1);
        }
        else if (StringSchema.class.isAssignableFrom(property.getClass())) {
            object.put(line, "string");
        }
        else if (DateSchema.class.isAssignableFrom(property.getClass())) {
            object.put(line, "01-01-2023");
        }
        else if (DateTimeSchema.class.isAssignableFrom(property.getClass())) {
            object.put(line, "01-01-2023T11:23:45Z");
        }
        else if (BooleanSchema.class.isAssignableFrom(property.getClass())) {
            object.put(line, true);
        }
    }

    private void appendOperation(StructuralNode node, HttpMethod method, String url) {
        if (StringUtils.isNotBlank(url)) {
            StringBuilder sb = new StringBuilder();
            if (method != null && !isEmpty(method.name())) {
                sb.append(method.name() + " ");
            }
            sb.append(url);
            sb.append(LINE_SEPARATOR);
            Block paragraph = new BlockImpl(node, "literal", sb.toString());
            node.append(paragraph);
        }
    }

    private void appendParameters(StructuralNode node, Operation operation) {
        List<Parameter> parameters = operation.getParameters() != null ? operation.getParameters() : new ArrayList<>();
        RequestBody requestBody = operation.getRequestBody();
        if (requestBody != null) {
            Parameter parameter = new Parameter().name("requestBody").required(true).in("Body").description(requestBody.getDescription());
            requestBody.getContent().values().stream().map(MediaType::getSchema).findFirst().ifPresent(parameter::setSchema);
            parameters.add(parameter);
        }
        parametersComponent.apply(node, parameters);
    }

    private void appendMediaTypes(StructuralNode node, RequestBody requestBody, ApiResponses apiResponses) {
        if (requestBody != null) {
            Set<String> consumes = requestBody.getContent().keySet();
            Section consumesSection = new SectionImpl(node);
            consumesSection.setTitle("Consumes");

            ListImpl consumesList = new ListImpl(consumesSection, "ulist");
            consumes.forEach(mediaType -> {
                ListItemImpl consumesEntry = new ListItemImpl(consumesSection, monospaced(mediaType));
                consumesList.append(consumesEntry);
            });

            consumesSection.append(consumesList);
            node.append(consumesSection);
        }

        Set<String> produces = apiResponses.values().stream().filter(apiResponse -> apiResponse.getContent() != null).map(apiResponse -> apiResponse.getContent().keySet()).flatMap(Collection::stream).collect(Collectors.toSet());
        if (!produces.isEmpty()) {
            Section producesSection = new SectionImpl(node);
            producesSection.setTitle("Produces");

            ListImpl mediaTypeList = new ListImpl(producesSection, "ulist");
            produces.forEach(mediaType -> {
                ListItemImpl producesEntry = new ListItemImpl(producesSection, monospaced(mediaType));
                mediaTypeList.append(producesEntry);
            });

            producesSection.append(mediaTypeList);
            node.append(producesSection);
        }
    }

    private void appendServersSection(StructuralNode node, List<Server> servers) {
        if (null == servers || servers.isEmpty()) return;

        Section serversSection = new SectionImpl(node);
        serversSection.setTitle(labels.getLabel(SECTION_TITLE_SERVERS));

        servers.forEach(server -> {
            Section serverSection = new SectionImpl(serversSection);
            serverSection.setTitle(italicUnconstrained(labels.getLabel(LABEL_SERVER)) + ": " + server.getUrl());

            appendDescription(serverSection, server.getDescription());
            ServerVariables variables = server.getVariables();
            appendVariables(serverSection, variables);
            serversSection.append(serverSection);
        });
        node.append(serversSection);
    }

    private void appendVariables(Section serverSection, ServerVariables variables) {
        if (null == variables || variables.isEmpty()) return;

        TableImpl serverVariables = new TableImpl(serverSection, new HashMap<String, Object>() {{
            put("header-option", "");
            put("cols", ".^2a,.^9a,.^3a,.^4a");
        }}, new ArrayList<>());
        serverVariables.setTitle(labels.getLabel(TABLE_TITLE_SERVER_VARIABLES));

        serverVariables.setHeaderRow(labels.getLabel(TABLE_HEADER_VARIABLE), labels.getLabel(TABLE_HEADER_DESCRIPTION),
            labels.getLabel(TABLE_HEADER_POSSIBLE_VALUES), labels.getLabel(TABLE_HEADER_DEFAULT)
        );

        variables.forEach((name, variable) -> {
            String possibleValues = String.join(", ", Optional.ofNullable(variable.getEnum()).orElse(Collections.singletonList("Any")));
            serverVariables.addRow(name, Optional.ofNullable(variable.getDescription()).orElse(""), possibleValues, variable.getDefault());

        });
        serverSection.append(serverVariables);
    }

    public static class Parameters {
        private final OpenAPI schema;

        public Parameters(OpenAPI schema) {
            this.schema = Validate.notNull(schema, "Schema must not be null");
        }
    }
}
