package io.github.swagger2markup.internal.document;

import io.github.swagger2markup.OpenAPI2MarkupConverter;
import io.github.swagger2markup.adoc.ast.impl.BlockImpl;
import io.github.swagger2markup.adoc.ast.impl.DocumentImpl;
import io.github.swagger2markup.adoc.ast.impl.ParagraphBlockImpl;
import io.github.swagger2markup.adoc.ast.impl.SectionImpl;
import io.github.swagger2markup.extension.MarkupComponent;
import io.github.swagger2markup.extension.OverviewDocumentExtension;
import io.github.swagger2markup.internal.component.ExternalDocumentationComponent;
import io.github.swagger2markup.internal.component.TagsComponent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.asciidoctor.ast.Block;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.Section;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import static io.github.swagger2markup.config.OpenAPILabels.LABEL_TERMS_OF_SERVICE;
import static io.github.swagger2markup.config.OpenAPILabels.SECTION_TITLE_OVERVIEW;
import static io.github.swagger2markup.extension.OverviewDocumentExtension.Context;
import static io.github.swagger2markup.internal.helper.OpenApiHelpers.appendDescription;

public class OverviewDocument extends MarkupComponent<Document, OverviewDocument.Parameters, Document> {
    private final TagsComponent tagsComponent;
    private final ExternalDocumentationComponent externalDocumentationComponent;

    public OverviewDocument(OpenAPI2MarkupConverter.OpenAPIContext context) {
        super(context);
        tagsComponent = new TagsComponent(context);
        this.externalDocumentationComponent = new ExternalDocumentationComponent(context);
    }

    public static OverviewDocument.Parameters parameters(OpenAPI schema) {
        return new OverviewDocument.Parameters(schema);
    }

    @Override
    public Document apply(Document document, Parameters parameters) {
        Info apiInfo = parameters.openAPI.getInfo();
        addDocumentTitle(document, apiInfo);

        applyOverviewDocumentExtension(new Context(OverviewDocumentExtension.Position.DOCUMENT_BEFORE, document));
        Document subDocument = new DocumentImpl(document);
        Section overviewDoc = new SectionImpl(subDocument, "section", new HashMap<>(), new ArrayList<>(),
            null, new ArrayList<>(), 1, "", new ArrayList<>(),
            null, null, "", "", false, false);
        applyOverviewDocumentExtension(new Context(OverviewDocumentExtension.Position.DOCUMENT_BEGIN, subDocument));
        overviewDoc.setTitle(labels.getLabel(SECTION_TITLE_OVERVIEW));

        appendDescription(overviewDoc, apiInfo.getDescription());
        addVersionInfo(overviewDoc, apiInfo);
        addContactInfo(overviewDoc, apiInfo);
        addURIScheme(overviewDoc, apiInfo);
        subDocument.append(overviewDoc);
        tagsComponent.apply(subDocument, parameters.openAPI.getTags());
        applyOverviewDocumentExtension(new Context(OverviewDocumentExtension.Position.DOCUMENT_END, subDocument));
        document.append(subDocument);

        externalDocumentationComponent.apply(document, parameters.openAPI.getExternalDocs());
        applyOverviewDocumentExtension(new Context(OverviewDocumentExtension.Position.DOCUMENT_AFTER, document));
        return document;
    }

    private void applyOverviewDocumentExtension(Context context) {
        extensionRegistry.getOverviewDocumentExtensions().forEach(extension -> extension.apply(context));
    }

    private void addDocumentTitle(Document rootDocument, Info apiInfo) {
        String title = apiInfo.getTitle();
        if (StringUtils.isNotBlank(title)) {
            rootDocument.setTitle(title);
        }
    }

    private void addVersionInfo(Section overviewDoc, Info info) {
        String version = info.getVersion();
        if (StringUtils.isNotBlank(version)) {
            Section versionDoc = new SectionImpl(overviewDoc, "section", new HashMap<>(), new ArrayList<>(),
                null, new ArrayList<>(), 2, "", new ArrayList<>(),
                null, null, "", "", false, false);
            versionDoc.setTitle("Version information");
            Block paragraph = new ParagraphBlockImpl(versionDoc);
            paragraph.setSource("Version :" + version);
            versionDoc.append(paragraph);
            overviewDoc.append(versionDoc);
        }
    }

    private void addContactInfo(Section overviewDoc, Info info) {
        Contact contact = info.getContact();
        if (StringUtils.isNotBlank(contact.getName()) && StringUtils.isNotBlank(contact.getEmail())) {
            Section contactDoc = new SectionImpl(overviewDoc, "section", new HashMap<>(), new ArrayList<>(),
                null, new ArrayList<>(), 2, "", new ArrayList<>(),
                null, null, "", "", false, false);
            contactDoc.setTitle("Contact information");
            Block paragraph = new ParagraphBlockImpl(contactDoc);
            paragraph.setLines(Arrays.asList("Contact : " + contact.getName(), "Contact Email : " + contact.getEmail()));
            contactDoc.append(paragraph);
            overviewDoc.append(contactDoc);
        }
    }

    private void addURIScheme(Section overviewDoc, Info info) {
        Contact contact = info.getContact();
        if (StringUtils.isNotBlank(contact.getName()) && StringUtils.isNotBlank(contact.getEmail())) {
            Section schemaDoc = new SectionImpl(overviewDoc, "section", new HashMap<>(), new ArrayList<>(),
                null, new ArrayList<>(), 2, "", new ArrayList<>(),
                null, null, "", "", false, false);
            schemaDoc.setTitle("URI scheme");
            Block paragraph = new ParagraphBlockImpl(schemaDoc);
            paragraph.setLines(Arrays.asList(
                "Host : " + "Test: https://api-test.maxxton.net , Production: https://api.maxxton.net",
                "BasePath : " + "/maxxton/v1",
                "Schemes : " + "HTTPS"
            ));
            schemaDoc.append(paragraph);
            overviewDoc.append(schemaDoc);
        }
    }

    private void addAuthorInfo(Document rootDocument, Info info) {
        Contact contact = info.getContact();
        if (null != contact) {
            String author = Optional.ofNullable(contact.getName()).orElse("");
            String email = contact.getEmail();
            if (StringUtils.isNotBlank(email)) {
                rootDocument.setAttribute("email", email, true);
            }
            rootDocument.setAttribute("author", author, true);
            rootDocument.setAttribute("authorcount", 1L, true);
        }
    }

    private void appendLicenseInfo(Section overviewDoc, Info info) {
        License license = info.getLicense();
        if (null != license) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isNotBlank(license.getUrl())) {
                sb.append(license.getUrl()).append("[");
            }
            sb.append(license.getName());
            if (StringUtils.isNotBlank(license.getUrl())) {
                sb.append("]");
            }
            BlockImpl paragraph = new ParagraphBlockImpl(overviewDoc);
            paragraph.setSource(sb.toString());
            overviewDoc.append(paragraph);
        }
    }

    private void appendTermsOfServiceInfo(Section overviewDoc, Info info) {
        String termsOfService = info.getTermsOfService();
        if (StringUtils.isNotBlank(termsOfService)) {
            Block paragraph = new ParagraphBlockImpl(overviewDoc);
            paragraph.setSource(termsOfService + "[" + labels.getLabel(LABEL_TERMS_OF_SERVICE) + "]");
            overviewDoc.append(paragraph);
        }
    }

    public static class Parameters {
        private final OpenAPI openAPI;

        public Parameters(OpenAPI openAPI) {
            this.openAPI = Validate.notNull(openAPI, "Schema must not be null");
        }
    }
}
