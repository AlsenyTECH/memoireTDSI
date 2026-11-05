package com.tdsi.memotheque.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import com.tdsi.memotheque.dao.DoubleFacteurDAO;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.DoubleFacteurDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.Role;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.PasswordUtil;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Totp;

/**
 * Double authentification par TOTP (RFC 6238) : après le mot de passe, un code à 6 chiffres
 * produit par une application (Google Authenticator, Aegis...) est demandé.
 * Le secret de 160 bits est chiffré en base (AES-256-GCM, clé du coffre, lié à l'utilisateur par
 * les données associées) ; un code déjà utilisé est refusé (anti-rejeu) ; les codes faux comptent
 * comme des échecs de connexion (verrouillage après cinq).
 */
public class DoubleFacteurService {

    public static final String EMETTEUR = "MémoTDSI UCAD";

    /** Secret proposé à l'utilisateur, pas encore enregistré tant qu'un code n'a pas été confirmé. */
    public record Enrolement(byte[] secret, String uri, String secretLisible) { }

    private final DoubleFacteurDAO dao;
    private final UtilisateurDAO utilisateurDAO;
    private final JournalService journal;

    public DoubleFacteurService() {
        this(new DoubleFacteurDAOImpl(), new UtilisateurDAOImpl(), new JournalService());
    }

    public DoubleFacteurService(DoubleFacteurDAO dao, UtilisateurDAO utilisateurDAO, JournalService journal) {
        this.dao = Objects.requireNonNull(dao);
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
        this.journal = Objects.requireNonNull(journal);
    }

    public boolean estActive(int idUtilisateur) throws SQLException {
        return dao.lire(idUtilisateur) != null;
    }

    /** Étape 1 : nouveau secret et lien otpauth:// (à afficher en QR code). */
    public Enrolement preparer() {
        Utilisateur u = exigerSession();
        byte[] secret = Totp.nouveauSecret();
        return new Enrolement(secret, Totp.uri(EMETTEUR, u.getEmail(), secret), Totp.lisible(secret));
    }

    /** Étape 2 : l'utilisateur prouve que son application est réglée en donnant le code affiché. */
    public void activer(Enrolement e, String code) throws SQLException {
        Utilisateur u = exigerSession();
        long pas = Totp.verifier(e.secret(), code, Instant.now().getEpochSecond());
        if (pas < 0) {
            throw new IllegalArgumentException("Code incorrect : vérifiez l'heure du téléphone et saisissez le code affiché.");
        }
        dao.enregistrer(u.getId(), chiffrer(u.getId(), e.secret()));
        dao.consommerPas(u.getId(), pas);
        journal.log(u.getId(), "DOUBLE_FACTEUR", "Double authentification activée");
    }

    /** Désactivation par l'utilisateur : mot de passe exigé. */
    public void desactiver(String motDePasse) throws SQLException {
        Utilisateur u = exigerSession();
        Utilisateur frais = utilisateurDAO.findById(u.getId());
        if (frais == null || motDePasse == null
                || !PasswordUtil.verifier(motDePasse, frais.getSel(), frais.getMotDePasseHash())) {
            throw new IllegalArgumentException("Mot de passe incorrect.");
        }
        dao.supprimer(u.getId());
        journal.log(u.getId(), "DOUBLE_FACTEUR", "Double authentification désactivée");
    }

    /** Téléphone perdu : la scolarité retire la double authentification d'un compte. */
    public void retirer(int idUtilisateur) throws SQLException {
        Utilisateur admin = exigerSession();
        if (!SessionManager.aLeRole(Role.ADMIN)) {
            throw new SecurityException("Réservé à l'administration.");
        }
        dao.supprimer(idUtilisateur);
        journal.log(admin.getId(), "DOUBLE_FACTEUR", "Double authentification retirée du compte #" + idUtilisateur);
    }

    /** Contrôle à la connexion : code valide dans la fenêtre de ±30 s et jamais utilisé. */
    boolean verifierCode(int idUtilisateur, String code, long epochSecondes) throws SQLException {
        DoubleFacteurDAO.Enregistrement e = dao.lire(idUtilisateur);
        if (e == null) {
            return true;
        }
        byte[] secret = dechiffrer(idUtilisateur, e.secretChiffre());
        try {
            long pas = Totp.verifier(secret, code, epochSecondes);
            return pas >= 0 && pas > e.dernierPas() && dao.consommerPas(idUtilisateur, pas);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static String chiffrer(int id, byte[] secret) {
        return Base64.getEncoder().encodeToString(
                CryptoUtil.chiffrer(GestionCles.cleChiffrementSecrets(), secret, aad(id)));
    }

    private static byte[] dechiffrer(int id, String b64) {
        return CryptoUtil.dechiffrer(GestionCles.cleChiffrementSecrets(), Base64.getDecoder().decode(b64), aad(id));
    }

    /** Le secret chiffré n'est valable que pour son propriétaire (copie vers un autre compte = échec GCM). */
    private static byte[] aad(int id) {
        return ("MemoTDSI-totp:" + id).getBytes(StandardCharsets.UTF_8);
    }

    private static Utilisateur exigerSession() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Connexion requise.");
        }
        return u;
    }
}
