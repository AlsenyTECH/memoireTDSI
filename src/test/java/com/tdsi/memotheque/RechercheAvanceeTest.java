package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireRechercheDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.MemoireCritere;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.TriMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.FichierService;
import com.tdsi.memotheque.service.JournalService;
import com.tdsi.memotheque.service.MemoireService;
import com.tdsi.memotheque.util.CitationUtil;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.SessionManager;

/** Recherche plein texte FTS5, filtre encadreur, pertinence, citations. */
class RechercheAvanceeTest {

    @TempDir
    Path dossier;

    private MemoireRechercheDAOImpl recherche;
    private Utilisateur encadreur1;
    private Utilisateur encadreur2;
    private Memoire securite;
    private Memoire crypto;
    private Memoire reseau;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        recherche = new MemoireRechercheDAOImpl();
        encadreur1 = BaseDeTest.creerUtilisateur("enc1@test.sn", null, Role.ENCADREUR, null, true);
        encadreur2 = BaseDeTest.creerUtilisateur("enc2@test.sn", null, Role.ENCADREUR, null, true);

        securite = publier("e1", "Sécurité des réseaux sans fil", "Étude des attaques sur le Wi-Fi WPA3 et leurs parades.",
                "sécurité, wifi, réseaux", encadreur1);
        crypto = publier("e2", "Cryptographie post-quantique", "Signatures Dilithium et chiffrement Kyber : implantation.",
                "cryptographie, post-quantique, signature", encadreur2);
        reseau = publier("e3", "Supervision réseau avec SNMP",
                "Supervision réseau d'un campus : réseau cœur, réseau d'accès, sécurité du réseau.",
                "réseau, supervision, snmp", encadreur1);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private Memoire publier(String login, String titre, String resume, String motsCles, Utilisateur enc)
            throws Exception {
        Utilisateur etu = BaseDeTest.creerUtilisateur(login + "@test.sn", "ETU-" + login.toUpperCase(),
                Role.ETUDIANT, Niveau.M2, true);
        MemoireService service = new MemoireService(new MemoireDAOImpl(),
                new com.tdsi.memotheque.dao.impl.DomaineDAOImpl(),
                new com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl(),
                new FichierService(dossier.resolve("storage")), new JournalService());
        Memoire m = BaseDeTest.memoireValide(BaseDeTest.premierDomaine(), enc, "2024-2025", Niveau.M2);
        m.setTitre(titre);
        m.setResume(resume);
        m.setMotsCles(motsCles);
        SessionManager.ouvrir(etu);
        service.deposer(m, BaseDeTest.creerPdf(dossier, login + ".pdf"));
        SessionManager.fermer();
        new ValidationDAOImpl().valider(m.getId(), enc.getId());
        return m;
    }

    private List<String> titres(String motCle, TriMemoire tri) throws Exception {
        MemoireCritere c = new MemoireCritere();
        c.setMotCle(motCle);
        c.setTri(tri);
        return recherche.rechercher(c).stream().map(Memoire::getTitre).toList();
    }

    @Test
    void insensibleAuxAccentsEtALaCasse() throws Exception {
        assertEquals(List.of(securite.getTitre()), titres("SECURITE wifi", TriMemoire.PLUS_RECENTS));
        assertTrue(titres("réseau", TriMemoire.PLUS_RECENTS).containsAll(List.of(securite.getTitre(), reseau.getTitre())));
        assertTrue(titres("reseau", TriMemoire.PLUS_RECENTS).containsAll(List.of(securite.getTitre(), reseau.getTitre())));
    }

    @Test
    void rechercheParPrefixeEtDansLeResume() throws Exception {
        assertEquals(List.of(crypto.getTitre()), titres("crypto", TriMemoire.PLUS_RECENTS));
        assertEquals(List.of(crypto.getTitre()), titres("kyber", TriMemoire.PLUS_RECENTS));
    }

    @Test
    void tousLesMotsSontExiges() throws Exception {
        assertEquals(List.of(reseau.getTitre()), titres("réseau snmp", TriMemoire.PLUS_RECENTS));
        assertTrue(titres("kyber snmp", TriMemoire.PLUS_RECENTS).isEmpty());
    }

    @Test
    void pertinenceBm25() throws Exception {
        // « réseau » apparaît bien plus souvent dans le mémoire de supervision.
        assertEquals(reseau.getTitre(), titres("réseau", TriMemoire.PERTINENCE).get(0));
        // Sans mot-clé, PERTINENCE se replie sur « plus récents » sans erreur.
        assertEquals(3, titres(null, TriMemoire.PERTINENCE).size());
    }

    @Test
    void syntaxeFtsEtInjectionNeutralisees() throws Exception {
        for (String piege : new String[] {"' OR '1'='1", "\"", "*", "titre:crypto", "NEAR(a b)", "a OR b",
                "-crypto", "^", "();--", "crypto\" OR \"x"}) {
            assertDoesNotThrow(() -> titres(piege, TriMemoire.PERTINENCE), piege);
        }
        assertTrue(titres("!!! ???", TriMemoire.PLUS_RECENTS).isEmpty(), "que de la ponctuation");
        assertTrue(titres("a OR b", TriMemoire.PLUS_RECENTS).isEmpty(), "OR traité comme un mot, pas un opérateur");
    }

    @Test
    void indexTenuAJourParLesTriggers() throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE memoire SET titre = ? WHERE id = ?")) {
            ps.setString(1, "Blockchain et contrats intelligents");
            ps.setInt(2, crypto.getId());
            ps.executeUpdate();
        }
        assertEquals(List.of("Blockchain et contrats intelligents"), titres("blockchain", TriMemoire.PLUS_RECENTS));
        assertTrue(titres("quantique", TriMemoire.PLUS_RECENTS).stream().noneMatch(t -> t.contains("Cryptographie")));

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM memoire WHERE id = ?")) {
            ps.setInt(1, crypto.getId());
            ps.executeUpdate();
        }
        assertTrue(titres("blockchain", TriMemoire.PLUS_RECENTS).isEmpty());
    }

    @Test
    void filtreParEncadreur() throws Exception {
        MemoireCritere c = new MemoireCritere();
        c.setIdEncadreur(encadreur1.getId());
        assertEquals(2, recherche.compter(c));
        c.setIdEncadreur(encadreur2.getId());
        assertEquals(List.of(crypto.getTitre()), recherche.rechercher(c).stream().map(Memoire::getTitre).toList());
    }

    @Test
    void citations() throws Exception {
        Memoire m = new MemoireDAOImpl().findById(crypto.getId());
        m.setTitre("Sécurité 100% & {LaTeX}_test");
        m.setCoAuteur("Awa Ndiaye");

        String apa = CitationUtil.apa(m);
        assertTrue(apa.startsWith("NomETUDIANT, P., & Awa Ndiaye (2025). Sécurité 100% & {LaTeX}_test ["), apa);
        assertTrue(apa.contains("[Mémoire de Master 2, Faculté TDSI"));

        String bib = CitationUtil.bibtex(m);
        assertTrue(bib.startsWith("@mastersthesis{nometudiant2025securite,"), bib);
        assertTrue(bib.contains("title  = {{Sécurité 100\\% \\& \\{LaTeX\\}\\_test}}"), bib);
        assertTrue(bib.contains("year   = {2025}"));
        assertTrue(bib.contains("author = {NomETUDIANT, Prenom and Awa Ndiaye}"));
    }
}
