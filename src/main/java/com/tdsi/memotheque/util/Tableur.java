package com.tdsi.memotheque.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Lecture des fichiers de la scolarité : Excel (.xlsx) et CSV, sans bibliothèque externe.
 * Un .xlsx est une archive ZIP de fichiers XML : on lit la première feuille et les chaînes partagées.
 *
 * Fichiers venus de l'extérieur, donc traités comme hostiles : taille limitée, décompression bornée
 * (pas de « bombe ZIP »), XML sans DOCTYPE ni entités externes (pas de XXE).
 *
 * Chaque ligne est rendue sous forme de { champ -> valeur }. Les en-têtes sont reconnus avec souplesse
 * (« Prénom », « N° carte étudiant », « Téléphone », « E-mail »...) et ramenés aux champs
 * {@link #PRENOM}, {@link #NOM}, {@link #MATRICULE}, {@link #TELEPHONE}, {@link #EMAIL}, {@link #NIVEAU}.
 */
public final class Tableur {

    public static final String PRENOM = "prenom";
    public static final String NOM = "nom";
    public static final String MATRICULE = "matricule";
    public static final String TELEPHONE = "telephone";
    public static final String EMAIL = "email";
    public static final String NIVEAU = "niveau";
    /** Numéro de la ligne dans le fichier (1 = en-tête), pour les messages d'erreur. */
    public static final String LIGNE = "_ligne";

    public static final long TAILLE_MAX_FICHIER = 5L * 1024 * 1024;
    private static final long TAILLE_MAX_XML = 20L * 1024 * 1024;
    public static final int LIGNES_MAX = 5000;

    private Tableur() { }

    /** Lit un fichier .xlsx ou .csv (UTF-8). */
    public static List<Map<String, String>> lire(Path fichier) throws IOException {
        if (Files.size(fichier) > TAILLE_MAX_FICHIER) {
            throw new IllegalArgumentException("Fichier trop volumineux (5 Mo maximum).");
        }
        String nom = fichier.getFileName().toString().toLowerCase(Locale.ROOT);
        List<List<String>> lignes;
        if (nom.endsWith(".xlsx")) {
            lignes = lireXlsx(fichier);
        } else if (nom.endsWith(".csv") || nom.endsWith(".txt")) {
            lignes = lireCsv(Files.readAllLines(fichier, StandardCharsets.UTF_8));
        } else if (nom.endsWith(".xls")) {
            throw new IllegalArgumentException("Ancien format Excel (.xls) : enregistrez le fichier au format .xlsx.");
        } else {
            throw new IllegalArgumentException("Format non pris en charge : fichier Excel (.xlsx) ou CSV attendu.");
        }
        return enEnregistrements(lignes);
    }

    // ------------------------------------------------------------------ en-têtes

    /** Ramène un en-tête libre à un champ connu, ou null. */
    static String champ(String entete) {
        String k = Normalizer.normalize(entete == null ? "" : entete, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (k.isEmpty()) {
            return null;
        }
        if (k.contains("prenom") || k.equals("firstname")) {
            return PRENOM;
        }
        if (k.contains("mail") || k.contains("courriel")) {
            return EMAIL;
        }
        if (k.contains("tel") || k.contains("phone") || k.contains("portable") || k.contains("mobile")) {
            return TELEPHONE;
        }
        if (k.contains("carte") || k.contains("matricule") || k.contains("numeroetudiant") || k.equals("ine")) {
            return MATRICULE;
        }
        if (k.contains("niveau") || k.contains("classe")) {
            return NIVEAU;
        }
        if (k.startsWith("nom") || k.equals("lastname")) {
            return NOM;
        }
        return null;
    }

    static List<Map<String, String>> enEnregistrements(List<List<String>> lignes) {
        int debut = 0;
        while (debut < lignes.size() && lignes.get(debut).stream().allMatch(String::isBlank)) {
            debut++;
        }
        if (debut == lignes.size()) {
            throw new IllegalArgumentException("Le fichier est vide.");
        }
        List<String> entetes = lignes.get(debut);
        String[] champs = new String[entetes.size()];
        int reconnus = 0;
        for (int i = 0; i < entetes.size(); i++) {
            champs[i] = champ(entetes.get(i));
            if (champs[i] != null) {
                reconnus++;
            }
        }
        if (reconnus == 0) {
            throw new IllegalArgumentException("En-têtes non reconnus : la première ligne doit contenir "
                    + "Prénom, Nom, Email (et N° carte étudiant, Téléphone, Niveau).");
        }
        if (lignes.size() - debut - 1 > LIGNES_MAX) {
            throw new IllegalArgumentException("Import limité à " + LIGNES_MAX + " lignes par fichier.");
        }
        List<Map<String, String>> resultat = new ArrayList<>();
        for (int i = debut + 1; i < lignes.size(); i++) {
            List<String> l = lignes.get(i);
            if (l.stream().allMatch(String::isBlank)) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            m.put(LIGNE, String.valueOf(i + 1));
            for (int c = 0; c < champs.length && c < l.size(); c++) {
                if (champs[c] != null && !m.containsKey(champs[c])) {
                    m.put(champs[c], l.get(c).trim());
                }
            }
            resultat.add(m);
        }
        return resultat;
    }

    // ------------------------------------------------------------------ CSV

    /** CSV au séparateur « ; » ou « , » (détecté sur l'en-tête), guillemets doubles acceptés. */
    static List<List<String>> lireCsv(List<String> lignes) {
        List<List<String>> res = new ArrayList<>();
        char sep = ';';
        boolean detecte = false;
        for (String brute : lignes) {
            String l = brute.replace("﻿", "");
            if (!detecte && !l.isBlank()) {
                sep = l.chars().filter(ch -> ch == ';').count() >= l.chars().filter(ch -> ch == ',').count() ? ';' : ',';
                detecte = true;
            }
            List<String> cellules = new ArrayList<>();
            StringBuilder cel = new StringBuilder();
            boolean guillemets = false;
            for (int i = 0; i < l.length(); i++) {
                char ch = l.charAt(i);
                if (ch == '"') {
                    if (guillemets && i + 1 < l.length() && l.charAt(i + 1) == '"') {
                        cel.append('"');
                        i++;
                    } else {
                        guillemets = !guillemets;
                    }
                } else if (ch == sep && !guillemets) {
                    cellules.add(cel.toString());
                    cel.setLength(0);
                } else {
                    cel.append(ch);
                }
            }
            cellules.add(cel.toString());
            res.add(cellules);
        }
        return res;
    }

    // ------------------------------------------------------------------ XLSX

    static List<List<String>> lireXlsx(Path fichier) throws IOException {
        try (ZipFile zip = new ZipFile(fichier.toFile())) {
            List<String> partagees = new ArrayList<>();
            ZipEntry ss = zip.getEntry("xl/sharedStrings.xml");
            if (ss != null) {
                Document d = xml(zip, ss);
                NodeList si = d.getElementsByTagNameNS("*", "si");
                for (int i = 0; i < si.getLength(); i++) {
                    StringBuilder texte = new StringBuilder();
                    NodeList t = ((Element) si.item(i)).getElementsByTagNameNS("*", "t");
                    for (int k = 0; k < t.getLength(); k++) {
                        texte.append(t.item(k).getTextContent());
                    }
                    partagees.add(texte.toString());
                }
            }
            ZipEntry feuille = premiereFeuille(zip);
            if (feuille == null) {
                throw new IllegalArgumentException("Classeur Excel sans feuille de calcul.");
            }
            Document d = xml(zip, feuille);
            List<List<String>> lignes = new ArrayList<>();
            NodeList rows = d.getElementsByTagNameNS("*", "row");
            for (int r = 0; r < rows.getLength(); r++) {
                Element row = (Element) rows.item(r);
                int numero = entier(row.getAttribute("r"), lignes.size() + 1);
                while (lignes.size() < numero - 1) {
                    lignes.add(new ArrayList<>());      // lignes vides sautées par Excel
                }
                TreeMap<Integer, String> cellules = new TreeMap<>();
                NodeList cs = row.getElementsByTagNameNS("*", "c");
                for (int c = 0; c < cs.getLength(); c++) {
                    Element cel = (Element) cs.item(c);
                    int col = colonne(cel.getAttribute("r"), cellules.isEmpty() ? 0 : cellules.lastKey() + 1);
                    cellules.put(col, valeur(cel, partagees));
                }
                List<String> ligne = new ArrayList<>();
                if (!cellules.isEmpty()) {
                    for (int c = 0; c <= cellules.lastKey(); c++) {
                        ligne.add(cellules.getOrDefault(c, ""));
                    }
                }
                lignes.add(ligne);
                if (lignes.size() > LIGNES_MAX + 10) {
                    throw new IllegalArgumentException("Import limité à " + LIGNES_MAX + " lignes par fichier.");
                }
            }
            return lignes;
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException("Ce fichier n'est pas un classeur Excel (.xlsx) valide.");
        }
    }

    private static ZipEntry premiereFeuille(ZipFile zip) {
        ZipEntry e = zip.getEntry("xl/worksheets/sheet1.xml");
        if (e != null) {
            return e;
        }
        return zip.stream().filter(z -> z.getName().startsWith("xl/worksheets/") && z.getName().endsWith(".xml"))
                .sorted((a, b) -> a.getName().compareTo(b.getName())).findFirst().orElse(null);
    }

    private static String valeur(Element cel, List<String> partagees) {
        String type = cel.getAttribute("t");
        if ("inlineStr".equals(type)) {
            StringBuilder s = new StringBuilder();
            NodeList t = cel.getElementsByTagNameNS("*", "t");
            for (int i = 0; i < t.getLength(); i++) {
                s.append(t.item(i).getTextContent());
            }
            return s.toString();
        }
        NodeList v = cel.getElementsByTagNameNS("*", "v");
        if (v.getLength() == 0) {
            return "";
        }
        String brut = v.item(0).getTextContent();
        if ("s".equals(type)) {
            int i = entier(brut, -1);
            return i >= 0 && i < partagees.size() ? partagees.get(i) : "";
        }
        if (type.isEmpty() || "n".equals(type)) {
            // Excel stocke 771234567 en nombre : on évite « 7.71234567E8 » et « 2026.0 ».
            try {
                java.math.BigDecimal d = new java.math.BigDecimal(brut);
                return d.stripTrailingZeros().scale() <= 0 ? d.toBigInteger().toString() : d.toPlainString();
            } catch (NumberFormatException e) {
                return brut;
            }
        }
        return brut;
    }

    /** « C12 » -> 2 (colonnes numérotées à partir de 0). */
    static int colonne(String reference, int parDefaut) {
        if (reference == null || reference.isEmpty()) {
            return parDefaut;
        }
        int col = 0;
        int i = 0;
        while (i < reference.length() && Character.isLetter(reference.charAt(i))) {
            col = col * 26 + (Character.toUpperCase(reference.charAt(i)) - 'A' + 1);
            i++;
        }
        return i == 0 || col > 16384 ? parDefaut : col - 1;
    }

    private static int entier(String s, int parDefaut) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return parDefaut;
        }
    }

    /** Lit une entrée XML de l'archive, décompression bornée, sans DOCTYPE ni entités externes. */
    private static Document xml(ZipFile zip, ZipEntry entree) throws IOException {
        byte[] octets;
        try (InputStream in = zip.getInputStream(entree)) {
            octets = lireBorne(in, TAILLE_MAX_XML);
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(octets));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalArgumentException("Classeur Excel illisible ou non conforme.");
        }
    }

    private static byte[] lireBorne(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(tampon)) > 0) {
            total += n;
            if (total > max) {
                throw new IllegalArgumentException("Classeur Excel trop volumineux une fois décompressé.");
            }
            out.write(tampon, 0, n);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ modèle Excel

    /** Écrit un classeur .xlsx (une feuille, en-tête en gras, colonnes larges) : sert de modèle d'import. */
    public static void ecrireXlsx(Path fichier, List<String> entetes, List<List<String>> lignes) throws IOException {
        StringBuilder feuille = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols>");
        for (int i = 0; i < entetes.size(); i++) {
            feuille.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                    .append("\" width=\"24\" customWidth=\"1\"/>");
        }
        feuille.append("</cols><sheetData>");
        List<List<String>> toutes = new ArrayList<>();
        toutes.add(entetes);
        toutes.addAll(lignes);
        for (int r = 0; r < toutes.size(); r++) {
            feuille.append("<row r=\"").append(r + 1).append("\">");
            List<String> l = toutes.get(r);
            for (int c = 0; c < l.size(); c++) {
                feuille.append("<c r=\"").append((char) ('A' + c)).append(r + 1).append("\" t=\"inlineStr\"")
                        .append(r == 0 ? " s=\"1\"" : "").append("><is><t>").append(echapperXml(l.get(c)))
                        .append("</t></is></c>");
            }
            feuille.append("</row>");
        }
        feuille.append("</sheetData></worksheet>");

        try (OutputStream o = Files.newOutputStream(fichier); ZipOutputStream zip = new ZipOutputStream(o)) {
            entree(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                    + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                    + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                    + "</Types>");
            entree(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                    + "</Relationships>");
            entree(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                    + "<sheets><sheet name=\"Comptes\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entree(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                    + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                    + "</Relationships>");
            entree(zip, "xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                    + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                    + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                    + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                    + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                    + "<cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                    + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs>"
                    + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                    + "</styleSheet>");
            entree(zip, "xl/worksheets/sheet1.xml", feuille.toString());
        }
    }

    private static void entree(ZipOutputStream zip, String nom, String contenu) throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String echapperXml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
