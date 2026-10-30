package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.AuthService;
import com.tdsi.memotheque.service.DonneesDemo;
import com.tdsi.memotheque.service.SignatureService;
import com.tdsi.memotheque.service.SoutenanceService;
import com.tdsi.memotheque.util.SessionManager;

/** Jeu de démonstration : comptes demandés, mémoires à chaque étape, chargement unique. */
class DonneesDemoTest {

    @TempDir
    Path dossier;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void comptesDemandesEtCircuitComplet() throws Exception {
        assertTrue(new DonneesDemo().charger());
        assertFalse(new DonneesDemo().charger(), "chargé une seule fois");
        assertNull(SessionManager.getUtilisateur(), "aucune session laissée ouverte");

        AuthService auth = new AuthService();
        Utilisateur admin = auth.connecter("admin@mcs", "admin123");
        assertEquals(Role.ADMIN, admin.getRole());
        Utilisateur samb = auth.connecter("samb@mcs", "samb123");
        assertEquals(Role.ENCADREUR, samb.getRole());
        assertEquals("SAMB", samb.getNom());
        Utilisateur babs = auth.connecter("babs@mcs", "babs123");
        assertEquals("Babacar", babs.getPrenom());
        assertEquals("NDIAYE", babs.getNom());
        assertFalse(babs.isDoitChangerMotDePasse());

        Map<StatutMemoire, Long> etats = new MemoireDAOImpl().findAll().stream()
                .collect(Collectors.groupingBy(Memoire::getStatut, Collectors.counting()));
        assertEquals(1, etats.get(StatutMemoire.EN_ATTENTE));
        assertEquals(1, etats.get(StatutMemoire.REFUSE));
        assertEquals(3, etats.get(StatutMemoire.AUTORISE), "Babacar, Khady, Pape");
        assertEquals(3, etats.get(StatutMemoire.VALIDE));

        // Babacar a une soutenance programmée avec M. SAMB dans le jury.
        SessionManager.ouvrir(babs);
        List<com.tdsi.memotheque.model.Soutenance> s = new SoutenanceService().soutenances();
        assertEquals(1, s.size());
        assertNotNull(s.get(0).roleDe(samb.getId()));

        // Les PV publiés sont signés et vérifiables.
        for (Memoire m : new MemoireDAOImpl().findAll()) {
            if (m.getStatut() == StatutMemoire.VALIDE) {
                assertEquals(SignatureService.Etat.VALIDE, new SignatureService().verifier(m.getId()).etat());
            }
        }
    }

    @Test
    void lesPdfDeDemonstrationSontDeVraisPdf() throws Exception {
        java.lang.reflect.Method pdf = DonneesDemo.class.getDeclaredMethod("pdf", String.class, String.class,
                String.class, com.tdsi.memotheque.model.Niveau.class, String.class);
        pdf.setAccessible(true);
        byte[] octets = (byte[]) pdf.invoke(null, "Titre (test) é", "Awa SOW", "M. SAMB",
                com.tdsi.memotheque.model.Niveau.M2, "Résumé du mémoire.");
        String texte = new String(octets, StandardCharsets.ISO_8859_1);
        assertTrue(texte.startsWith("%PDF-1.4"));
        assertTrue(texte.contains("startxref") && texte.endsWith("%%EOF\n"));
        assertTrue(texte.contains("Titre \\(test\\)"), "parenthèses échappées");
        int debut = texte.indexOf("xref\n");
        int annonce = Integer.parseInt(texte.substring(texte.indexOf("startxref\n") + 10, texte.indexOf("\n%%EOF")).trim());
        assertEquals(debut, annonce, "table xref à la bonne position");
    }
}
