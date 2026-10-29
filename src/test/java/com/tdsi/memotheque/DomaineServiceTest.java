package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.DomaineDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.DomaineService;
import com.tdsi.memotheque.util.SessionManager;

/** [M6] Tests de DomaineService et DomaineDAOImpl (base temporaire). */
class DomaineServiceTest {

    @TempDir
    Path dossier;

    private DomaineService service;
    private DomaineDAOImpl dao;
    private Utilisateur admin;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        service = new DomaineService();
        dao = new DomaineDAOImpl();
        admin = BaseDeTest.creerUtilisateur("admin@test.sn", null, Role.ADMIN, null, true);
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private Domaine trouver(String nom) throws Exception {
        return dao.findAll().stream().filter(d -> d.getNom().equals(nom)).findFirst().orElse(null);
    }

    @Test
    void seedChargeLesDixDomaines() throws Exception {
        assertEquals(10, service.lister().size());
    }

    @Test
    void ajouterModifierSupprimer() throws Exception {
        SessionManager.ouvrir(admin);

        service.ajouter("  Blockchain  ", "Registres distribués");
        Domaine d = trouver("Blockchain");
        assertNotNull(d);
        assertEquals("Registres distribués", d.getDescription());

        service.modifier(d.getId(), "Blockchain et cryptomonnaies", "  ");
        Domaine lu = dao.findById(d.getId());
        assertEquals("Blockchain et cryptomonnaies", lu.getNom());
        assertNull(lu.getDescription());

        service.supprimer(d.getId());
        assertNull(dao.findById(d.getId()));

        assertEquals(1, Journal.compter("AJOUT_DOMAINE"));
        assertEquals(1, Journal.compter("MODIFICATION_DOMAINE"));
        assertEquals(1, Journal.compter("SUPPRESSION_DOMAINE"));
    }

    @Test
    void nomEnDoubleOuInvalideRefuse() throws Exception {
        SessionManager.ouvrir(admin);
        assertThrows(IllegalArgumentException.class, () -> service.ajouter("Cryptographie", null));
        assertThrows(IllegalArgumentException.class, () -> service.ajouter(" ", null));
        assertThrows(IllegalArgumentException.class, () -> service.ajouter("x".repeat(101), null));
        assertEquals(10, service.lister().size());
    }

    @Test
    void domaineUtiliseNonSupprimable() throws Exception {
        Utilisateur etu = BaseDeTest.creerUtilisateur("e@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        Utilisateur enc = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        BaseDeTest.deposer(dossier, etu, enc, "2024-2025", Niveau.M2);
        Domaine utilise = BaseDeTest.premierDomaine();

        SessionManager.ouvrir(admin);
        assertThrows(IllegalStateException.class, () -> service.supprimer(utilise.getId()));
        assertNotNull(dao.findById(utilise.getId()));
    }

    @Test
    void reserveALadmin() throws Exception {
        SessionManager.ouvrir(BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true));
        int id = BaseDeTest.premierDomaine().getId();

        assertThrows(SecurityException.class, () -> service.ajouter("Nouveau domaine", null));
        assertThrows(SecurityException.class, () -> service.modifier(id, "Autre nom", null));
        assertThrows(SecurityException.class, () -> service.supprimer(id));
        assertEquals(10, service.lister().size());
    }
}
