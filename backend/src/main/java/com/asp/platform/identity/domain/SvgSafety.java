package com.asp.platform.identity.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/// Strict allow-list check for SVG logos. A logo is refused, never cleaned, when it has:
/// - a DOCTYPE or entity declaration (XXE, billion laughs),
/// - a processing instruction such as `<?xml-stylesheet?>`,
/// - an element outside the SVG namespace or outside the drawing allow-list below (so no `script`, `style`,
///   `foreignObject`, `image`, `a`, `iframe`, `animate`, `set`, ...),
/// - an event-handler attribute (`on*`),
/// - a link (`href`, `xlink:href`) that is not a fragment inside the file (`#id`),
/// - an attribute value with `javascript:`, `vbscript:`, `data:`, `@import`, `expression(` or a `url(...)` that
///   does not point to `#id`.
public final class SvgSafety {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
            "svg", "g", "defs", "title", "desc", "symbol", "use",
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
            "text", "tspan",
            "linearGradient", "radialGradient", "stop", "clipPath", "mask");
    private static final Pattern URL_REFERENCE = Pattern.compile("url\\((?!['\"]?#)");
    private static final Set<String> FORBIDDEN_IN_VALUES = Set.of(
            "javascript:", "vbscript:", "data:", "@import", "expression(");

    private SvgSafety() {
    }

    /// @throws InvalidLogoException with `logo-unsafe` or `logo-unsupported` when the file is refused
    public static void check(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (text.contains("<!doctype") || text.contains("<!entity")) {
            throw unsafe("SVG logos may not contain a DOCTYPE or entity declarations");
        }
        Element root = parse(content).getDocumentElement();
        if (!"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) {
            throw new InvalidLogoException(InvalidLogoException.UNSUPPORTED, "The logo must be a PNG, SVG or WEBP image");
        }
        checkTree(root.getOwnerDocument());
    }

    private static org.w3c.dom.Document parse(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT);
            return builder.parse(new ByteArrayInputStream(content));
        } catch (SAXException | IOException e) {
            throw new InvalidLogoException(InvalidLogoException.UNSUPPORTED, "The SVG file could not be read");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The JDK XML parser does not support secure processing", e);
        }
    }

    private static void checkTree(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> {
                    checkElement((Element) child);
                    checkTree(child);
                }
                case Node.PROCESSING_INSTRUCTION_NODE, Node.ENTITY_REFERENCE_NODE, Node.DOCUMENT_TYPE_NODE ->
                        throw unsafe("SVG logos may not contain processing instructions or entities");
                default -> {
                    // Text, CDATA and comments are inert.
                }
            }
        }
    }

    private static void checkElement(Element element) {
        String name = element.getLocalName();
        if (!SVG_NS.equals(element.getNamespaceURI()) || name == null || !ALLOWED_ELEMENTS.contains(name)) {
            throw unsafe("SVG logos may only contain drawing elements; <" + element.getNodeName() + "> is not allowed");
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            checkAttribute((Attr) attributes.item(i));
        }
    }

    private static void checkAttribute(Attr attribute) {
        String local = attribute.getLocalName() != null ? attribute.getLocalName() : attribute.getName();
        String name = local.toLowerCase(Locale.ROOT);
        String value = attribute.getValue().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("on")) {
            throw unsafe("SVG logos may not contain event handlers such as " + attribute.getName());
        }
        if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
            return;
        }
        if (name.equals("href") && !value.startsWith("#")) {
            throw unsafe("SVG logos may only link to elements inside the file");
        }
        if (URL_REFERENCE.matcher(value).find() || FORBIDDEN_IN_VALUES.stream().anyMatch(value::contains)) {
            throw unsafe("SVG logos may not reference other files or run script");
        }
    }

    private static InvalidLogoException unsafe(String message) {
        return new InvalidLogoException(InvalidLogoException.UNSAFE, message);
    }

    /// Keeps parser warnings off stderr; errors still fail the parse.
    private static final ErrorHandler SILENT = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
            // ignored
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };
}
