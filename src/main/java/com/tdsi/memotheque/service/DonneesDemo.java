package com.tdsi.memotheque.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Domaine;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Niveau;
import com.tdsi.memotheque.model.NiveauDiffusion;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.DatabaseConnection;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;

/**
 * Jeu de démonstration pour tester l'application : comptes de tous les rôles et mémoires à toutes
 * les étapes du circuit (soumis, à corriger, autorisé, soutenance programmée, PV à saisir, publiés).
 *
 * Activé seulement en mode démonstration (-Dmemotdsi.demo=true, posé par « mvn javafx:run »,
 * ou MEMOTDSI_DEMO=1) : l'application installée n'a pas ces comptes aux mots de passe simples.
 * Tout passe par les vrais services : PDF chiffrés, journal, notifications, PV signés.
 * Chargé une seule fois (rien n'est fait si admin@mcs existe déjà).
 */
public class DonneesDemo {

    private static final Logger LOG = Logger.getLogger(DonneesDemo.class.getName());

    /** Comptes affichés sur l'écran de connexion en mode démonstration. */
    public record Compte(String role, String nom, String identifiant, String motDePasse) { }

    public static final List<Compte> COMPTES = List.of(
            new Compte("Scolarité", "Administrateur MCS", "admin@mcs", "admin123"),
            new Compte("Encadreur", "M. SAMB", "samb@mcs", "samb123"),
            new Compte("Étudiant", "Babacar NDIAYE", "babs@mcs", "babs123"));

    static final String MDP_ENSEIGNANTS = "prof123";
    static final String MDP_ETUDIANTS = "etu123";

    private final UtilisateurDAO utilisateurs = new UtilisateurDAOImpl();

    public static boolean active() {
        return Boolean.getBoolean("memotdsi.demo") || "1".equals(System.getenv("MEMOTDSI_DEMO"));
    }

    /** Crée le jeu de démonstration s'il n'existe pas encore. Retourne true s'il vient d'être créé. */
    public boolean charger() throws SQLException, IOException {
        if (utilisateurs.existeEmail("admin@mcs")) {
            return false;
        }
        Utilisateur precedent = SessionManager.getUtilisateur();
        try {
            creerTout();
            LOG.info("Jeu de démonstration créé (admin@mcs, samb@mcs, babs@mcs...).");
            return true;
        } finally {
            if (precedent == null) {
                SessionManager.fermer();
            } else {
                SessionManager.ouvrir(precedent);
            }
        }
    }

    private void creerTout() throws SQLException, IOException {
        // ---------------------------------------------------------------- comptes
        Utilisateur admin = compte(null, "MCS", "Administrateur", "admin@mcs", "admin123", Role.ADMIN, null);
        Utilisateur samb = compte(null, "SAMB", "M.", "samb@mcs", "samb123", Role.ENCADREUR, null);
        Utilisateur diop = compte(null, "DIOP", "Fatou", "diop@mcs", MDP_ENSEIGNANTS, Role.ENCADREUR, null);
        Utilisateur faye = compte(null, "FAYE", "Ousmane", "faye@mcs", MDP_ENSEIGNANTS, Role.ENCADREUR, null);
        Utilisateur sarr = compte(null, "SARR", "Aminata", "sarr@mcs", MDP_ENSEIGNANTS, Role.ENCADREUR, null);
        Utilisateur gueye = compte(null, "GUEYE", "Ibrahima", "gueye@mcs", MDP_ENSEIGNANTS, Role.ENCADREUR, null);

        Utilisateur babs = compte("MCS-2026-001", "NDIAYE", "Babacar", "babs@mcs", "babs123", Role.ETUDIANT, Niveau.M2);
        Utilisateur awa = etudiant("MCS-2026-002", "SOW", "Awa", "awa@mcs", Niveau.M2);
        Utilisateur moussa = etudiant("MCS-2026-003", "BA", "Moussa", "moussa@mcs", Niveau.M2);
        Utilisateur mariama = etudiant("MCS-2026-004", "DIOP", "Mariama", "mariama@mcs", Niveau.M2);
        Utilisateur khady = etudiant("MCS-2026-005", "MBAYE", "Khady", "khady@mcs", Niveau.M2);
        Utilisateur pape = etudiant("TDSI-2026-031", "NDOUR", "Pape", "pape@mcs", Niveau.L3);
        Utilisateur aissatou = etudiant("MCS-2026-006", "KANE", "Aïssatou", "aissatou@mcs", Niveau.M1);
        Utilisateur cheikh = etudiant("TDSI-2026-044", "CISSÉ", "Cheikh", "cheikh@mcs", Niveau.L3);
        etudiant("MCS-2026-007", "BA", "Fatoumata", "fatoumata@mcs", Niveau.M1);

        // ---------------------------------------------------------------- campagne
        SessionManager.ouvrir(admin);
        ParametreService parametres = new ParametreService();
        parametres.definir(ParametreService.anneeParDefaut(LocalDate.now()), true, null);

        // ---------------------------------------------------------------- dépôts
        Memoire mBabs = deposer(babs, samb, "Sécurité des signatures post-quantiques sur cartes à puce",
                "Cryptographie", "post-quantique, Dilithium, carte à puce, canaux auxiliaires",
                "Ce mémoire évalue l'implantation de la signature Dilithium sur carte à puce : performances, "
                        + "consommation mémoire et résistance aux attaques par canaux auxiliaires.", NiveauDiffusion.PUBLIC);
        Memoire mAwa = deposer(awa, samb, "Analyse des attaques par canaux auxiliaires sur AES",
                "Cryptographie", "AES, analyse de consommation, DPA, contre-mesures",
                "Étude expérimentale des attaques DPA et CPA contre une implantation logicielle d'AES-128 "
                        + "et évaluation de contre-mesures par masquage.", NiveauDiffusion.PUBLIC);
        Memoire mMoussa = deposer(moussa, faye, "Chiffrement homomorphe pour le calcul en nuage",
                "Cloud et virtualisation", "chiffrement homomorphe, cloud, confidentialité",
                "Comparaison des schémas BFV et CKKS pour des calculs statistiques sur des données chiffrées "
                        + "hébergées dans le nuage.", NiveauDiffusion.PUBLIC);
        Memoire mMariama = deposer(mariama, gueye, "Investigation numérique sur terminaux Android",
                "Forensique et investigation numérique", "forensique, Android, preuve numérique",
                "Méthodologie d'acquisition et d'analyse de preuves numériques sur terminaux Android "
                        + "chiffrés, dans le respect de la chaîne de custody.", NiveauDiffusion.RESTREINT);
        Memoire mKhady = deposer(khady, samb, "Gestion des clés dans les réseaux LoRaWAN",
                "Internet des objets (IoT)", "LoRaWAN, IoT, gestion de clés",
                "Analyse du cycle de vie des clés dans LoRaWAN 1.1 et proposition d'un mécanisme de "
                        + "renouvellement adapté aux capteurs à faible énergie.", NiveauDiffusion.PUBLIC);
        Memoire mPape = deposer(pape, gueye, "Supervision d'un réseau d'entreprise avec SNMPv3",
                "Réseaux et télécommunications", "SNMPv3, supervision, sécurité réseau",
                "Mise en place d'une supervision sécurisée avec SNMPv3 (authentification et chiffrement) "
                        + "pour un réseau d'entreprise de taille moyenne.", NiveauDiffusion.PUBLIC);
        Memoire mAissatou = deposer(aissatou, samb, "Détection d'intrusions par apprentissage automatique",
                "Intelligence artificielle appliquée à la sécurité", "IDS, apprentissage automatique, réseaux",
                "Évaluation de modèles d'apprentissage automatique pour la détection d'intrusions sur le "
                        + "jeu de données CIC-IDS2017.", NiveauDiffusion.PUBLIC);
        Memoire mCheikh = deposer(cheikh, diop, "Audit de sécurité d'une application web de e-santé",
                "Sécurité des applications web", "OWASP, audit, e-santé",
                "Audit d'une application de prise de rendez-vous médicaux selon le référentiel OWASP ASVS "
                        + "et plan de correction priorisé.", NiveauDiffusion.PUBLIC);

        // ---------------------------------------------------------------- avis des encadreurs
        ValidationService validation = new ValidationService();
        SessionManager.ouvrir(samb);
        validation.donnerAvisFavorable(mBabs.getId());
        validation.donnerAvisFavorable(mAwa.getId());
        validation.donnerAvisFavorable(mKhady.getId());
        SessionManager.ouvrir(faye);
        validation.donnerAvisFavorable(mMoussa.getId());
        SessionManager.ouvrir(gueye);
        validation.donnerAvisFavorable(mMariama.getId());
        validation.donnerAvisFavorable(mPape.getId());
        SessionManager.ouvrir(diop);
        validation.refuser(mCheikh.getId(), "Reprendre l'analyse de risques (méthode EBIOS RM) et citer les sources du chapitre 2.");
        // mAissatou reste « soumis » : M. SAMB a un mémoire à évaluer.

        // ---------------------------------------------------------------- soutenances
        SessionManager.ouvrir(admin);
        SoutenanceService soutenances = new SoutenanceService();
        LocalDate j = LocalDate.now();
        // À venir : Babacar NDIAYE, dans une semaine (convocations pour lui et le jury).
        soutenances.planifier(mBabs.getId(), j.plusDays(7).atTime(10, 0), "Amphi A",
                diop.getId(), List.of(faye.getId()));
        // Passées : on les programme puis on les ramène à leur vraie date.
        soutenances.planifier(mAwa.getId(), j.plusDays(1).atTime(9, 0), "Amphi A", sarr.getId(), List.of(gueye.getId()));
        soutenances.planifier(mMoussa.getId(), j.plusDays(1).atTime(11, 0), "Salle 12", diop.getId(), List.of(samb.getId()));
        soutenances.planifier(mMariama.getId(), j.plusDays(1).atTime(14, 0), "Salle 12", faye.getId(), List.of(sarr.getId()));
        soutenances.planifier(mPape.getId(), j.plusDays(1).atTime(16, 0), "Amphi B", sarr.getId(), List.of(samb.getId()));
        dater(mAwa.getId(), j.minusDays(21).atTime(9, 0));
        dater(mMoussa.getId(), j.minusDays(21).atTime(11, 0));
        dater(mMariama.getId(), j.minusDays(14).atTime(14, 0));
        dater(mPape.getId(), j.minusDays(1).atTime(16, 0));   // PV à saisir par la scolarité

        soutenances.enregistrerProcesVerbal(mAwa.getId(), 15.5,
                "Travail rigoureux, très bonne maîtrise de l'analyse DPA.", null, null, "admin123");
        soutenances.enregistrerProcesVerbal(mMoussa.getId(), 17.25,
                "Excellent travail, résultats publiables.", null, null, "admin123");
        soutenances.enregistrerProcesVerbal(mMariama.getId(), 12.75,
                "Bonne démarche ; partie juridique à approfondir.", NiveauDiffusion.RESTREINT, null, "admin123");
        // mKhady : autorisée, en attente de planification par la scolarité.
    }

    // ---------------------------------------------------------------- outils

    private Utilisateur etudiant(String matricule, String nom, String prenom, String email, Niveau niveau)
            throws SQLException {
        return compte(matricule, nom, prenom, email, MDP_ETUDIANTS, Role.ETUDIANT, niveau);
    }

    private Utilisateur compte(String matricule, String nom, String prenom, String email, String motDePasse,
            Role role, Niveau niveau) throws SQLException {
        String sel = PasswordUtil.genererSel();
        Utilisateur u = new Utilisateur();
        u.setMatricule(matricule);
        u.setNom(nom);
        u.setPrenom(prenom);
        u.setEmail(email);
        u.setSel(sel);
        u.setMotDePasseHash(PasswordUtil.hasher(motDePasse, sel));
        u.setRole(role);
        u.setNiveau(niveau);
        u.setActif(true);
        u.setDateCreation(LocalDate.now());
        utilisateurs.insert(u);
        return u;
    }

    private Memoire deposer(Utilisateur etudiant, Utilisateur encadreur, String titre, String domaine,
            String motsCles, String resume, NiveauDiffusion diffusion) throws SQLException, IOException {
        SessionManager.ouvrir(etudiant);
        MemoireService service = new MemoireService();
        Domaine d = service.listerDomaines().stream().filter(x -> x.getNom().equals(domaine)).findFirst()
                .orElse(service.listerDomaines().get(0));
        Memoire m = new Memoire();
        m.setTitre(titre);
        m.setResume(resume);
        m.setMotsCles(motsCles);
        m.setDomaine(d);
        m.setEncadreur(encadreur);
        m.setNiveauDiffusion(diffusion);
        Path pdf = Files.createTempFile("memoire-demo", ".pdf");
        try {
            Files.write(pdf, pdf(titre, etudiant.getNomComplet(), encadreur.getNomComplet(), etudiant.getNiveau(), resume));
            return service.deposer(m, pdf, true);
        } finally {
            Files.deleteIfExists(pdf);
        }
    }

    private static void dater(int idMemoire, LocalDateTime quand) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE soutenance SET date_heure = ? WHERE id_memoire = ?")) {
            ps.setString(1, quand.toString());
            ps.setInt(2, idMemoire);
            ps.executeUpdate();
        }
    }

    /** PDF d'une page (page de garde) écrit à la main : en-tête, titre, auteur, encadreur, résumé. */
    static byte[] pdf(String titre, String auteur, String encadreur, Niveau niveau, String resume) {
        List<String[]> lignes = new ArrayList<>();
        lignes.add(new String[] {"F1", "11", "Université Cheikh Anta Diop de Dakar - Filière TDSI"});
        lignes.add(new String[] {"F1", "11", "Mémoire de " + (niveau == null ? "fin d'études" : niveau.name())});
        lignes.add(new String[] {"F2", "18", ""});
        for (String l : couper(titre, 48)) {
            lignes.add(new String[] {"F2", "18", l});
        }
        lignes.add(new String[] {"F1", "12", ""});
        lignes.add(new String[] {"F1", "12", "Présenté par : " + auteur});
        lignes.add(new String[] {"F1", "12", "Sous la direction de : " + encadreur});
        lignes.add(new String[] {"F1", "12", ""});
        lignes.add(new String[] {"F2", "12", "Résumé"});
        for (String l : couper(resume, 85)) {
            lignes.add(new String[] {"F1", "11", l});
        }
        StringBuilder flux = new StringBuilder("BT 72 760 Td\n");
        for (String[] l : lignes) {
            int taille = Integer.parseInt(l[1]);
            flux.append('/').append(l[0]).append(' ').append(taille).append(" Tf 0 -").append(taille + 8)
                    .append(" Td (").append(echapper(l[2])).append(") Tj\n");
        }
        flux.append("ET\n");
        Charset cp1252 = Charset.forName("windows-1252");
        byte[] contenu = flux.toString().getBytes(cp1252);

        String[] objets = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
                    + "/Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> >>",
            null,
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> positions = new ArrayList<>();
        ecrire(out, "%PDF-1.4\n%âãÏÓ\n", cp1252);
        for (int i = 0; i < objets.length; i++) {
            positions.add(out.size());
            ecrire(out, (i + 1) + " 0 obj\n", cp1252);
            if (objets[i] == null) {
                ecrire(out, "<< /Length " + contenu.length + " >>\nstream\n", cp1252);
                out.writeBytes(contenu);
                ecrire(out, "endstream\n", cp1252);
            } else {
                ecrire(out, objets[i] + "\n", cp1252);
            }
            ecrire(out, "endobj\n", cp1252);
        }
        int xref = out.size();
        StringBuilder fin = new StringBuilder("xref\n0 " + (objets.length + 1) + "\n0000000000 65535 f \n");
        for (int p : positions) {
            fin.append(String.format("%010d 00000 n \n", p));
        }
        fin.append("trailer\n<< /Size ").append(objets.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        ecrire(out, fin.toString(), cp1252);
        return out.toByteArray();
    }

    private static void ecrire(ByteArrayOutputStream out, String s, Charset cs) {
        out.writeBytes(s.getBytes(cs));
    }

    private static String echapper(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                .replace('’', '\'').replace('œ', 'o');
    }

    private static List<String> couper(String texte, int largeur) {
        List<String> lignes = new ArrayList<>();
        StringBuilder l = new StringBuilder();
        for (String mot : texte.split(" ")) {
            if (l.length() + mot.length() + 1 > largeur && l.length() > 0) {
                lignes.add(l.toString());
                l.setLength(0);
            }
            if (l.length() > 0) {
                l.append(' ');
            }
            l.append(mot);
        }
        if (l.length() > 0) {
            lignes.add(l.toString());
        }
        return lignes;
    }
}
