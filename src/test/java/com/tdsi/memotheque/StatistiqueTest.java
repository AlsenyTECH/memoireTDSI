package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireRechercheDAOImpl;
import com.tdsi.memotheque.dao.impl.StatistiqueDAOImpl;
import com.tdsi.memotheque.dao.impl.ValidationDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.service.StatistiqueService;
import com.tdsi.memotheque.util.SessionManager;

/** [M5] Tests de StatistiqueDAOImpl et StatistiqueService (base temporaire). */
class StatistiqueTest {

    @TempDir
    Path dossier;

    private StatistiqueDAOImpl dao;
    private Memoire m1;
    private Memoire m2;
    private Utilisateur etu1;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        dao = new StatistiqueDAOImpl();

        etu1 = BaseDeTest.creerUtilisateur("e1@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        Utilisateur etu2 = BaseDeTest.creerUtilisateur("e2@test.sn", "ETU-0002", Role.ETUDIANT, Niveau.L3, true);
        Utilisateur enc = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);

        m1 = BaseDeTest.deposer(dossier, etu1, enc, "2023-2024", Niveau.M1);
        m2 = BaseDeTest.deposer(dossier, etu1, enc, "2024-2025", Niveau.M2);
        BaseDeTest.deposer(dossier, etu2, enc, "2024-2025", Niveau.L3);   // reste EN_ATTENTE

        ValidationDAOImpl validation = new ValidationDAOImpl();
        validation.valider(m1.getId(), enc.getId());
        validation.valider(m2.getId(), enc.getId());

        MemoireRechercheDAOImpl compteurs = new MemoireRechercheDAOImpl();
        compteurs.incrementerVues(m2.getId());
        compteurs.incrementerVues(m2.getId());
        compteurs.incrementerVues(m1.getId());
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    @Test
    void totaux() throws Exception {
        assertEquals(2, dao.totalMemoiresValides());
        assertEquals(2, dao.totalEtudiants());
    }

    @Test
    void repartitionsSurLesMemoiresPublies() throws Exception {
        Map<String, Integer> parAnnee = dao.memoiresParAnnee();
        assertEquals(Map.of("2023-2024", 1, "2024-2025", 1), parAnnee);
        assertEquals(List.of("2023-2024", "2024-2025"), List.copyOf(parAnnee.keySet()));

        assertEquals(Map.of("M1", 1, "M2", 1), dao.memoiresParNiveau());

        Map<String, Integer> parDomaine = dao.memoiresParDomaine();
        assertEquals(1, parDomaine.size());
        assertEquals(2, parDomaine.values().iterator().next());
    }

    @Test
    void repartitionParStatutCompteTout() throws Exception {
        assertEquals(Map.of("EN_ATTENTE", 1, "VALIDE", 2), dao.memoiresParStatut());
    }

    @Test
    void plusConsultesTriesParVues() throws Exception {
        List<Memoire> top = dao.plusConsultes(5);
        assertEquals(2, top.size());
        assertEquals(m2.getId(), top.get(0).getId());
        assertEquals(2, top.get(0).getNbVues());
        assertEquals(1, dao.plusConsultes(1).size());
        assertThrows(IllegalArgumentException.class, () -> dao.plusConsultes(0));
    }

    @Test
    void serviceExigeUneConnexion() throws Exception {
        StatistiqueService service = new StatistiqueService();
        assertThrows(SecurityException.class, service::totalMemoiresValides);

        SessionManager.ouvrir(etu1);
        assertEquals(2, service.totalMemoiresValides());
        assertEquals(1, service.totalEnAttente());
        assertEquals(Map.of("En attente", 1, "Validé", 2), service.memoiresParStatut());
        assertEquals(2, service.plusConsultes().size());
    }
}
