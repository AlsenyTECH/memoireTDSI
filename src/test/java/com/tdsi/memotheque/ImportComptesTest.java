package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.CompteService;
import com.tdsi.memotheque.service.CompteService.LigneImport;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Tableur;

/** Comptes par type (étudiant, enseignant, personnel administratif), téléphone, import Excel / CSV. */
class ImportComptesTest {

    @TempDir
    Path dossier;

    private CompteService service;
    private UtilisateurDAOImpl dao;
    private Utilisateur admin;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new CompteService();
        dao = new UtilisateurDAOImpl();
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
        SessionManager.ouvrir(admin);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void creationDesTroisTypesDeComptes() throws Exception {
        CompteService.CompteCree e = service.creerCompte(Role.ETUDIANT, "Awa", "SOW", "Awa.Sow@UCAD.edu.sn",
                "+221 77 123 45 67", "mcs-2026-010", Niveau.M2);
        CompteService.CompteCree p = service.creerCompte(Role.ENCADREUR, "Mamadou", "DIALLO", "m.diallo@ucad.edu.sn",
                "76 987 65 43", "ignoré", null);
        CompteService.CompteCree a = service.creerCompte(Role.ADMIN, "Fatou", "NDIAYE", "scolarite@ucad.edu.sn",
                null, null, null);

        Utilisateur etu = dao.findByEmailOuMatricule("awa.sow@ucad.edu.sn");
        assertEquals("MCS-2026-010", etu.getMatricule());
        assertEquals("+221 77 123 45 67", etu.getTelephone());
        assertEquals(Niveau.M2, etu.getNiveau());
        assertTrue(etu.isDoitChangerMotDePasse() && etu.isActif());
        assertNull(dao.findByEmailOuMatricule("m.diallo@ucad.edu.sn").getMatricule(), "pas de carte pour un enseignant");
        assertEquals(Role.ADMIN, dao.findByEmailOuMatricule("scolarite@ucad.edu.sn").getRole());
        assertNotNull(new AuthService().connecter("awa.sow@ucad.edu.sn", e.motDePasseTemporaire()));
        assertNotNull(new AuthService().connecter("m.diallo@ucad.edu.sn", p.motDePasseTemporaire()));
        assertNotNull(new AuthService().connecter("scolarite@ucad.edu.sn", a.motDePasseTemporaire()));
        assertEquals(3, Journal.compter("CREATION_COMPTE"));
    }

    @Test
    void reglesDeCreation() {
        assertThrows(IllegalArgumentException.class, () -> service.creerCompte(Role.ETUDIANT, "A", "B", "a@b.sn",
                null, null, Niveau.L1), "carte obligatoire pour un étudiant");
        assertThrows(IllegalArgumentException.class, () -> service.creerCompte(Role.ETUDIANT, "A", "B", "a@b.sn",
                null, "ETU-1234", null), "niveau obligatoire");
        assertThrows(IllegalArgumentException.class, () -> service.creerCompte(Role.ENCADREUR, "A", "B", "a@b.sn",
                "abc", null, null), "téléphone invalide");
        assertThrows(IllegalArgumentException.class, () -> service.creerCompte(Role.ENCADREUR, "A", "B", "admin@test.sn",
                null, null, null), "e-mail déjà pris");
        SessionManager.fermer();
        assertThrows(SecurityException.class, () -> service.creerCompte(Role.ADMIN, "A", "B", "x@y.sn", null, null, null));
    }

    @Test
    void importExcelDesEtudiantsDepuisLeModele() throws Exception {
        Path xlsx = dossier.resolve("etudiants.xlsx");
        Tableur.ecrireXlsx(xlsx, List.of("Prénom", "Nom", "N° carte étudiant", "Téléphone", "E-mail", "Niveau"), List.of(
                List.of("Awa", "SOW", "MCS-2026-010", "+221 77 123 45 67", "awa.sow@ucad.edu.sn", "M2"),
                List.of("Moussa", "BA", "MCS-2026-011", "", "moussa.ba@ucad.edu.sn", ""),
                List.of("", "", "", "", "", ""),
                List.of("Fail", "X", "MCS-2026-012", "12", "pas-un-email", "L1")));

        List<LigneImport> r = service.importerFichier(Role.ETUDIANT, Niveau.L3, xlsx);
        assertEquals(3, r.size(), "la ligne vide est ignorée");
        assertEquals("CREE", r.get(0).resultat());
        assertEquals("CREE", r.get(1).resultat());
        assertEquals("ERREUR", r.get(2).resultat());
        assertEquals(5, r.get(2).numero(), "numéro de ligne du tableur");
        assertEquals(Niveau.L3, dao.findByEmailOuMatricule("moussa.ba@ucad.edu.sn").getNiveau(), "niveau par défaut");
        assertEquals("+221 77 123 45 67", dao.findByEmailOuMatricule("MCS-2026-010").getTelephone());

        // Réimport : réinscription (pas de doublon), nouveau niveau.
        Tableur.ecrireXlsx(xlsx, List.of("Prénom", "Nom", "N° carte étudiant", "Email", "Niveau"),
                List.of(List.of("Awa", "SOW", "MCS-2026-010", "awa.sow@ucad.edu.sn", "M1")));
        assertEquals("REINSCRIT", service.importerFichier(Role.ETUDIANT, null, xlsx).get(0).resultat());
        assertEquals(Niveau.M1, dao.findByEmailOuMatricule("awa.sow@ucad.edu.sn").getNiveau());
    }

    @Test
    void importDesEnseignantsEtDuPersonnelSansCarte() throws Exception {
        Path csv = dossier.resolve("enseignants.csv");
        Files.writeString(csv, "Prénom;Nom;Téléphone;Email\nMamadou;DIALLO;77 000 11 22;m.diallo@ucad.edu.sn\n"
                + "Fatou;NDIAYE;;admin@test.sn\n", StandardCharsets.UTF_8);
        List<LigneImport> r = service.importerFichier(Role.ENCADREUR, null, csv);
        assertEquals("CREE", r.get(0).resultat());
        assertEquals("ERREUR", r.get(1).resultat(), "l'administrateur existant n'est pas transformé en enseignant");
        assertEquals(Role.ENCADREUR, dao.findByEmailOuMatricule("m.diallo@ucad.edu.sn").getRole());
        assertEquals(Role.ADMIN, dao.findByEmailOuMatricule("admin@test.sn").getRole());

        Path modele = dossier.resolve("modele.xlsx");
        CompteService.ecrireModele(Role.ADMIN, modele);
        List<Map<String, String>> lu = Tableur.lire(modele);
        assertEquals(1, lu.size());
        assertEquals("scolarite.mcs@ucad.edu.sn", lu.get(0).get(Tableur.EMAIL));
        assertEquals(1, service.importerFichier(Role.ADMIN, null, modele).stream()
                .filter(l -> l.resultat().equals("CREE")).count());
        assertEquals(Role.ADMIN, dao.findByEmailOuMatricule("scolarite.mcs@ucad.edu.sn").getRole());
    }

    @Test
    void lecteurExcelNombresEtEnTetesLibres() throws Exception {
        // Classeur écrit « comme Excel » : chaînes partagées et téléphone stocké en nombre.
        Path xlsx = dossier.resolve("excel.xlsx");
        String ns = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"";
        zip(xlsx, Map.of(
                "xl/sharedStrings.xml", "<sst " + ns + "><si><t>Prénoms</t></si><si><t>NOM</t></si>"
                        + "<si><t>Numéro de carte d'étudiant</t></si><si><t>Tél. portable</t></si><si><t>Adresse mail</t></si>"
                        + "<si><r><t>Kh</t></r><r><t>ady</t></r></si><si><t>MBAYE</t></si><si><t>khady@ucad.edu.sn</t></si></sst>",
                "xl/worksheets/sheet1.xml", "<worksheet " + ns + "><sheetData>"
                        + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c>"
                        + "<c r=\"D1\" t=\"s\"><v>3</v></c><c r=\"E1\" t=\"s\"><v>4</v></c></row>"
                        + "<row r=\"3\"><c r=\"A3\" t=\"s\"><v>5</v></c><c r=\"B3\" t=\"s\"><v>6</v></c><c r=\"C3\"><v>20260015</v></c>"
                        + "<c r=\"D3\"><v>7.71234567E8</v></c><c r=\"E3\" t=\"s\"><v>7</v></c></row>"
                        + "</sheetData></worksheet>"));
        List<Map<String, String>> l = Tableur.lire(xlsx);
        assertEquals(1, l.size());
        assertEquals("Khady", l.get(0).get(Tableur.PRENOM));
        assertEquals("MBAYE", l.get(0).get(Tableur.NOM));
        assertEquals("20260015", l.get(0).get(Tableur.MATRICULE));
        assertEquals("771234567", l.get(0).get(Tableur.TELEPHONE), "pas de notation scientifique");
        assertEquals("khady@ucad.edu.sn", l.get(0).get(Tableur.EMAIL));
        assertEquals("3", l.get(0).get(Tableur.LIGNE));
    }

    @Test
    void fichiersHostilesRefuses() throws Exception {
        // XXE : une entité externe qui lirait un fichier local.
        Path xxe = dossier.resolve("xxe.xlsx");
        zip(xxe, Map.of("xl/worksheets/sheet1.xml", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData><row r=\"1\">"
                + "<c r=\"A1\" t=\"inlineStr\"><is><t>&e;</t></is></c></row></sheetData></worksheet>"));
        assertThrows(IllegalArgumentException.class, () -> Tableur.lire(xxe));

        Path faux = dossier.resolve("faux.xlsx");
        Files.writeString(faux, "pas un zip");
        assertThrows(IllegalArgumentException.class, () -> Tableur.lire(faux));

        Path xls = dossier.resolve("ancien.xls");
        Files.writeString(xls, "x");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Tableur.lire(xls));
        assertTrue(e.getMessage().contains(".xlsx"));

        Path sansEntete = dossier.resolve("vide.csv");
        Files.writeString(sansEntete, "a;b;c\n1;2;3\n");
        assertThrows(IllegalArgumentException.class, () -> Tableur.lire(sansEntete));
    }

    private static void zip(Path fichier, Map<String, String> entrees) throws Exception {
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(fichier))) {
            for (var e : entrees.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }
}
