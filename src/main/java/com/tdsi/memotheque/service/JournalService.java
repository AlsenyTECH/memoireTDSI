package com.tdsi.memotheque.service;







import com.tdsi.memotheque.dao.JournalDAO;
import com.tdsi.memotheque.dao.impl.JournalDAOImpl;
import com.tdsi.memotheque.model.JournalActivite;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.SessionManager;

public class JournalService {

    private final JournalDAO journalDAO;

    public JournalService() {
        this.journalDAO = new JournalDAOImpl();
    }

    public JournalService(JournalDAO journalDAO) {
        this.journalDAO = journalDAO;
    }


    
    
    public void log(String action, String details) {
        Integer idUtilisateur = null;

        try {
            Utilisateur utilisateur = SessionManager.getUtilisateur();

            if (utilisateur != null) {
                idUtilisateur = utilisateur.getId();
            }
        } catch (Exception e) {
            // L'identifiant reste null en cas de problème.
        }

        log(idUtilisateur, action, details);
    }

 
    
    public void log(Integer idUtilisateur, String action, String details) {
        try {
            JournalActivite activite = new JournalActivite();

            activite.setIdUtilisateur(idUtilisateur);
            activite.setAction(action);
            activite.setDetails(details);

            journalDAO.enregistrer(activite);

        } catch (Exception e) {
            
            java.util.logging.Logger.getLogger(JournalService.class.getName()).log(
                java.util.logging.Level.SEVERE, "Impossible d'enregistrer l'activité « " + action + " »", e);
        }
    }

    /** [M6] Dernières entrées du journal (au plus 200). Lecture réservée à l'administrateur. */
    public java.util.List<JournalActivite> derniers(int limite) throws java.sql.SQLException {
        if (!SessionManager.aLeRole(com.tdsi.memotheque.model.Role.ADMIN)) {
            throw new SecurityException("Journal réservé à l'administrateur.");
        }
        return journalDAO.derniers(limite);
    }

    /** Vérifie la chaîne HMAC complète du journal. Réservé à l'administrateur, et journalisé. */
    public com.tdsi.memotheque.model.VerificationJournal verifierIntegrite() throws java.sql.SQLException {
        if (!SessionManager.aLeRole(com.tdsi.memotheque.model.Role.ADMIN)) {
            throw new SecurityException("Vérification réservée à l'administrateur.");
        }
        com.tdsi.memotheque.model.VerificationJournal resultat = journalDAO.verifierChaine();
        log("VERIFICATION_JOURNAL", (resultat.intact() ? "OK : " : "ALERTE : ") + resultat.message());
        return resultat;
    }
}

