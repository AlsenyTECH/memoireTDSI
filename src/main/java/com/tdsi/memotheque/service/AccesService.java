package com.tdsi.memotheque.service;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;

import com.tdsi.memotheque.dao.SoutenanceDAO;
import com.tdsi.memotheque.dao.impl.SoutenanceDAOImpl;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;

/**
 * Règles d'accès à un mémoire, au même endroit pour tous les écrans.
 *
 * Fiche (notice) au catalogue : mémoire publié et hors embargo.
 * PDF :
 *  - administration, auteur, encadreur et membres du jury : toujours ;
 *  - mémoire publié, hors embargo : diffusion libre -> tout utilisateur connecté ;
 *    diffusion restreinte -> enseignants seulement.
 */
public class AccesService {

    private final SoutenanceDAO soutenanceDAO;

    public AccesService() {
        this(new SoutenanceDAOImpl());
    }

    public AccesService(SoutenanceDAO soutenanceDAO) {
        this.soutenanceDAO = Objects.requireNonNull(soutenanceDAO);
    }

    public boolean noticeVisible(Memoire m, LocalDate aujourdhui) {
        return m.getStatut() == StatutMemoire.VALIDE && !m.estSousEmbargo(aujourdhui);
    }

    public boolean peutLirePdf(Utilisateur u, Memoire m, LocalDate aujourdhui) throws SQLException {
        if (u == null || m == null) {
            return false;
        }
        if (u.getRole() == Role.ADMIN || estImplique(u, m)) {
            return true;
        }
        if (!noticeVisible(m, aujourdhui)) {
            return false;
        }
        NiveauDiffusion d = m.getNiveauDiffusion() == null ? NiveauDiffusion.PUBLIC : m.getNiveauDiffusion();
        return d != NiveauDiffusion.RESTREINT || u.getRole() == Role.ENCADREUR;
    }

    /** Auteur, encadreur ou membre du jury de ce mémoire. */
    public boolean estImplique(Utilisateur u, Memoire m) throws SQLException {
        if (m.getAuteur() != null && m.getAuteur().getId() == u.getId()) {
            return true;
        }
        if (m.getEncadreur() != null && m.getEncadreur().getId() == u.getId()) {
            return true;
        }
        Soutenance s = soutenanceDAO.findByMemoire(m.getId());
        return s != null && s.roleDe(u.getId()) != null;
    }

    /** Message expliquant un refus d'accès au PDF. */
    public String raisonRefus(Memoire m, LocalDate aujourdhui) {
        if (m.getStatut() != StatutMemoire.VALIDE) {
            return "Ce mémoire n'est pas encore publié.";
        }
        if (m.estSousEmbargo(aujourdhui)) {
            return "Ce mémoire est confidentiel jusqu'au " + m.getDateFinEmbargo() + ".";
        }
        return "Diffusion restreinte : le PDF est réservé aux enseignants et à l'administration.";
    }
}
