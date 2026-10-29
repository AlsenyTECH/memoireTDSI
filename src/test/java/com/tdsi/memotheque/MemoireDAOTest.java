package com.tdsi.memotheque;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Mention;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;

/** [M3] Tests de MemoireDAOImpl (base SQLite temporaire). */
class MemoireDAOTest {

    @TempDir
    Path dossier;

    private MemoireDAOImpl dao;
    private Utilisateur etudiant;
    private Utilisateur encadreur;
    private Domaine domaine;

    @BeforeEach
    void preparer() throws Exception {
        BaseDeTest.ouvrirBaseTemporaire(dossier);
        dao = new MemoireDAOImpl();
        etudiant = BaseDeTest.creerUtilisateur("etu@test.sn", "ETU-0001", Role.ETUDIANT, Niveau.M2, true);
        encadreur = BaseDeTest.creerUtilisateur("enc@test.sn", null, Role.ENCADREUR, null, true);
        domaine = BaseDeTest.premierDomaine();
    }

    @AfterEach
    void nettoyer() {
        BaseDeTest.fermer();
    }

    private Memoire nouveau(String annee, Niveau niveau) {
        Memoire m = BaseDeTest.memoireValide(domaine, encadreur, annee, niveau);
        m.setAuteur(etudiant);
        m.setDateDepot(LocalDate.of(2025, 6, 1));
        m.setDateSoutenance(LocalDate.of(2025, 5, 20));
        m.setMention(Mention.BIEN);
        m.setCheminFichier("storage/abc.pdf");
        m.setNomOriginal("memoire.pdf");
        m.setTailleOctets(1234);
        m.setEmpreinteSha256("a".repeat(64));
        return m;
    }

    @Test
    void insertPuisFindById() throws Exception {
        Memoire m = nouveau("2024-2025", Niveau.M2);
        dao.insert(m);

        assertTrue(m.getId() > 0);

        Memoire lu = dao.findById(m.getId());
        assertNotNull(lu);
        assertEquals(m.getTitre(), lu.getTitre());
        assertEquals(Niveau.M2, lu.getNiveau());
        assertEquals(StatutMemoire.EN_ATTENTE, lu.getStatut());
        assertEquals(Mention.BIEN, lu.getMention());
        assertEquals(LocalDate.of(2025, 5, 20), lu.getDateSoutenance());
        assertEquals(domaine.getNom(), lu.getDomaineNom());
        assertEquals(etudiant.getId(), lu.getAuteur().getId());
        assertEquals(encadreur.getId(), lu.getEncadreur().getId());
        assertNull(lu.getValidateur());
        assertEquals(1234, lu.getTailleOctets());
    }

    @Test
    void findByIdInexistantRetourneNull() throws Exception {
        assertNull(dao.findById(999));
    }

    @Test
    void existePourAuteurNiveauAnnee() throws Exception {
        dao.insert(nouveau("2024-2025", Niveau.M2));

        assertTrue(dao.existePourAuteurNiveauAnnee(etudiant.getId(), Niveau.M2, "2024-2025"));
        assertFalse(dao.existePourAuteurNiveauAnnee(etudiant.getId(), Niveau.M1, "2024-2025"));
        assertFalse(dao.existePourAuteurNiveauAnnee(etudiant.getId(), Niveau.M2, "2023-2024"));
    }

    @Test
    void contrainteUniqueAuteurNiveauAnnee() throws Exception {
        dao.insert(nouveau("2024-2025", Niveau.M2));
        assertThrows(SQLException.class, () -> dao.insert(nouveau("2024-2025", Niveau.M2)));
    }

    @Test
    void findByAuteurEtFindAll() throws Exception {
        dao.insert(nouveau("2024-2025", Niveau.M2));
        dao.insert(nouveau("2023-2024", Niveau.M1));

        Utilisateur autre = BaseDeTest.creerUtilisateur("autre@test.sn", "ETU-0002",
                Role.ETUDIANT, Niveau.L3, true);
        Memoire m3 = nouveau("2024-2025", Niveau.L3);
        m3.setAuteur(autre);
        dao.insert(m3);

        List<Memoire> siens = dao.findByAuteur(etudiant.getId());
        assertEquals(2, siens.size());
        assertTrue(siens.stream().allMatch(m -> m.getAuteur().getId() == etudiant.getId()));
        assertEquals(3, dao.findAll().size());
    }

    @Test
    void updatePuisDelete() throws Exception {
        Memoire m = nouveau("2024-2025", Niveau.M2);
        dao.insert(m);

        m.setTitre("Nouveau titre du mémoire");
        m.setStatut(StatutMemoire.REFUSE);
        m.setMotifRefus("Bibliographie insuffisante");
        m.setValidateur(encadreur);
        dao.update(m);

        Memoire lu = dao.findById(m.getId());
        assertEquals("Nouveau titre du mémoire", lu.getTitre());
        assertEquals(StatutMemoire.REFUSE, lu.getStatut());
        assertEquals("Bibliographie insuffisante", lu.getMotifRefus());
        assertEquals(encadreur.getId(), lu.getValidateur().getId());

        dao.delete(m.getId());
        assertNull(dao.findById(m.getId()));
        assertThrows(SQLException.class, () -> dao.delete(m.getId()));
    }

    @Test
    void injectionSqlDansLeTitreStockeeTelleQuelle() throws Exception {
        Memoire m = nouveau("2024-2025", Niveau.M2);
        m.setTitre("x'); DROP TABLE memoire; --");
        dao.insert(m);

        assertEquals("x'); DROP TABLE memoire; --", dao.findById(m.getId()).getTitre());
        assertEquals(1, dao.findAll().size());
    }
}
