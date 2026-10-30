package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.service.ConvocationService;
import com.tdsi.memotheque.service.ConvocationService.Convocation;
import com.tdsi.memotheque.service.DonneesDemo;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

/** Convocations : une lettre par destinataire, chacun ne voit que la sienne, la scolarité voit tout. */
class ConvocationTest {

    @TempDir
    Path dossier;

    private ConvocationService service;
    private int idBabs;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        new DonneesDemo().charger();
        service = new ConvocationService();
        Utilisateur babs = new UtilisateurDAOImpl().findByEmailOuMatricule("babs@mcs");
        idBabs = new MemoireDAOImpl().findByAuteur(babs.getId()).get(0).getId();
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private void en(String email) throws Exception {
        SessionManager.ouvrir(new UtilisateurDAOImpl().findByEmailOuMatricule(email));
    }

    @Test
    void laScolariteObtientLaLettreDeChacun() throws Exception {
        en("admin@mcs");
        List<Convocation> l = service.convocations(idBabs);
        assertEquals(4, l.size(), "étudiant + président + examinateur + encadreur");
        assertEquals("Babacar NDIAYE", l.get(0).destinataire());
        assertEquals("Candidat(e)", l.get(0).qualite());
        assertTrue(l.stream().anyMatch(c -> c.destinataire().equals("M. SAMB") && c.qualite().equals("Encadreur")));
        assertTrue(l.stream().anyMatch(c -> c.qualite().equals("Président du jury")));
        assertTrue(l.get(0).paragraphes().get(1).contains("Amphi A"));
        assertTrue(l.get(0).details().stream().anyMatch(d -> d[0].equals("Heure") && d[1].startsWith("10h00")));
    }

    @Test
    void chacunNeVoitQueSaConvocation() throws Exception {
        en("babs@mcs");
        List<Convocation> l = service.convocations(idBabs);
        assertEquals(1, l.size());
        assertEquals("Convocation à la soutenance de votre mémoire", l.get(0).objet());

        en("samb@mcs");
        l = service.convocations(idBabs);
        assertEquals(1, l.size());
        assertTrue(l.get(0).paragraphes().get(0).contains("en qualité d'encadreur"), l.get(0).paragraphes().get(0));
        assertTrue(l.get(0).paragraphes().get(0).contains("Babacar NDIAYE"));

        en("awa@mcs");
        assertThrows(SecurityException.class, () -> service.convocations(idBabs));
        en("gueye@mcs");
        assertThrows(SecurityException.class, () -> service.convocations(idBabs), "hors du jury");
        SessionManager.fermer();
        assertThrows(SecurityException.class, () -> service.convocations(idBabs));
    }

    @Test
    void pasDeConvocationSansSoutenanceProgrammee() throws Exception {
        en("admin@mcs");
        Memoire publie = new MemoireDAOImpl().findAll().stream()
                .filter(m -> m.getStatut() == com.tdsi.memotheque.model.StatutMemoire.VALIDE).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> service.convocations(publie.getId()), "soutenance déjà tenue");
    }
}
