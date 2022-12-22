/*
 * Copyright 2017 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.swagger2markup.internal.component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;

import io.github.swagger2markup.OpenAPI2MarkupConverter;
import io.github.swagger2markup.adoc.ast.impl.SectionImpl;
import io.github.swagger2markup.adoc.ast.impl.TableImpl;
import io.github.swagger2markup.extension.MarkupComponent;
import io.swagger.v3.oas.models.responses.ApiResponse;

import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_DESCRIPTION;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_HTTP_CODE;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_HEADER_SCHEMA;
import static io.github.swagger2markup.config.OpenAPILabels.TABLE_TITLE_RESPONSES;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.generateInnerDoc;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.getSchemaTypeAsString;

public class ResponseComponent extends MarkupComponent<StructuralNode, ResponseComponent.Parameters, StructuralNode> {

    private final HeadersComponent headersComponent;
    private final LinkComponent linkComponent;
    private final MediaContentComponent mediaContentComponent;

    public ResponseComponent(OpenAPI2MarkupConverter.OpenAPIContext context) {
        super(context);
        this.headersComponent = new HeadersComponent(context);
        this.linkComponent = new LinkComponent(context);
        this.mediaContentComponent = new MediaContentComponent(context);
    }

    public static Parameters parameters(Map<String, ApiResponse> apiResponses) {
        return new Parameters(apiResponses);
    }

    public StructuralNode apply(StructuralNode serverSection, Map<String, ApiResponse> apiResponses) {
        return apply(serverSection, parameters(apiResponses));
    }

    @Override
    public StructuralNode apply(StructuralNode serverSection, Parameters params) {
        Map<String, ApiResponse> apiResponses = params.apiResponses;

        if (null == apiResponses || apiResponses.isEmpty()) return serverSection;

        SectionImpl responseSection = new SectionImpl(serverSection);
        responseSection.setTitle(labels.getLabel(TABLE_TITLE_RESPONSES));

        TableImpl pathResponsesTable = new TableImpl(serverSection, new HashMap<>(), new ArrayList<>());
        pathResponsesTable.setOption("header");
        pathResponsesTable.setAttribute("caption", "", true);
        pathResponsesTable.setAttribute("cols", ".^2a,.^14a,.^4a", true);
        pathResponsesTable.setHeaderRow(
            labels.getLabel(TABLE_HEADER_HTTP_CODE),
            labels.getLabel(TABLE_HEADER_DESCRIPTION),
            labels.getLabel(TABLE_HEADER_SCHEMA));

        apiResponses.forEach((httpCode, apiResponse) -> {
            String schema = "No Content";
            if (apiResponse.getContent() != null) {
                schema = apiResponse.getContent().values().stream().map(mediaType -> mediaType.getSchema() != null ? getSchemaTypeAsString(mediaType.getSchema()) : "").collect(Collectors.joining());
            }
            pathResponsesTable.addRow(
                generateInnerDoc(pathResponsesTable, httpCode),
                getResponseDescriptionColumnDocument(pathResponsesTable, apiResponse),
                generateInnerDoc(pathResponsesTable, schema)
            );
        });
        responseSection.append(pathResponsesTable);
        serverSection.append(responseSection);
        return serverSection;
    }

    private Document getResponseDescriptionColumnDocument(Table table, ApiResponse apiResponse) {
        Document document = generateInnerDoc(table, Optional.ofNullable(apiResponse.getDescription()).orElse(""));
        headersComponent.apply(document, apiResponse.getHeaders());
        return document;
    }

    public static class Parameters {
        private final Map<String, ApiResponse> apiResponses;

        public Parameters(Map<String, ApiResponse> apiResponses) {

            this.apiResponses = apiResponses;
        }
    }
}
