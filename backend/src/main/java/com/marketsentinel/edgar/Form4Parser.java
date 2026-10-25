package com.marketsentinel.edgar;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Parses the XML of an SEC Form 4 ("Statement of changes in beneficial ownership") into its
 * non-derivative transaction lines (common stock bought, sold, granted...).
 *
 * The parser refuses DOCTYPEs and external entities: XML from the internet must never be able to
 * read local files or call other servers (XXE attacks).
 */
public final class Form4Parser {

    public record Owner(String name, String title) {
    }

    public record Line(int lineNo, LocalDate date, String code, BigDecimal shares, BigDecimal price, boolean acquired) {
    }

    public record Form4(Owner owner, List<Line> lines) {
    }

    private Form4Parser() {
    }

    public static Form4 parse(String xml) {
        try {
            Document doc = safeBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            Owner owner = owner(root);
            List<Line> lines = new ArrayList<>();
            NodeList txs = root.getElementsByTagName("nonDerivativeTransaction");
            for (int i = 0; i < txs.getLength(); i++) {
                Element tx = (Element) txs.item(i);
                String date = valueOf(tx, "transactionDate");
                String code = text(tx, "transactionCode");
                String shares = valueOf(tx, "transactionShares");
                if (date == null || code == null || shares == null) {
                    continue;
                }
                String price = valueOf(tx, "transactionPricePerShare");
                String ad = valueOf(tx, "transactionAcquiredDisposedCode");
                lines.add(new Line(i + 1, LocalDate.parse(date.substring(0, 10)), code.trim(), new BigDecimal(shares.trim()),
                        price == null || price.isBlank() ? null : new BigDecimal(price.trim()), "A".equals(ad)));
            }
            return new Form4(owner, lines);
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid Form 4 XML document", e);
        }
    }

    private static Owner owner(Element root) {
        Element owner = first(root, "reportingOwner");
        if (owner == null) {
            return new Owner("Unknown", "");
        }
        String name = text(owner, "rptOwnerName");
        List<String> roles = new ArrayList<>();
        String officerTitle = text(owner, "officerTitle");
        if (officerTitle != null && !officerTitle.isBlank()) {
            roles.add(officerTitle.trim());
        }
        if (isTrue(text(owner, "isDirector"))) {
            roles.add("Director");
        }
        if (isTrue(text(owner, "isTenPercentOwner"))) {
            roles.add("10% owner");
        }
        return new Owner(name == null ? "Unknown" : name.trim(), String.join(", ", roles));
    }

    private static boolean isTrue(String s) {
        return s != null && (s.trim().equals("1") || s.trim().equalsIgnoreCase("true"));
    }

    /** Form 4 wraps most values as <field><value>...</value></field>. */
    private static String valueOf(Element parent, String tag) {
        Element field = first(parent, tag);
        if (field == null) {
            return null;
        }
        Element value = first(field, "value");
        return value != null ? value.getTextContent() : field.getTextContent();
    }

    private static String text(Element parent, String tag) {
        Element e = first(parent, tag);
        return e == null ? null : e.getTextContent();
    }

    private static Element first(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return (Element) list.item(i);
            }
        }
        return null;
    }

    private static DocumentBuilder safeBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder();
    }
}
