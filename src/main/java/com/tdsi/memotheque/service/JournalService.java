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
            
            System.err.println(
                "Impossible d'enregistrer l'activité : "
                + e.getMessage()
            );
        }
    }
}

