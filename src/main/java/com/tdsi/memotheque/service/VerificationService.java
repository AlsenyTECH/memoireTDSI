package com.tdsi.memotheque.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.tdsi.memotheque.dao.DocumentEmisDAO;
import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.ParametreDAO;
import com.tdsi.memotheque.dao.SoutenanceDAO;
import com.tdsi.memotheque.dao.impl.DocumentEmisDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.ParametreDAOImpl;
import com.tdsi.memotheque.dao.impl.SoutenanceDAOImpl;
import com.tdsi.memotheque.model.DocumentEmis;
import com.tdsi.memotheque.model.MembreJury;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.Soutenance;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.GestionCles;
import com.tdsi.memotheque.util.Libelles;
import com.tdsi.memotheque.util.SessionManager;
import com.tdsi.memotheque.util.Totp;

/**
 * Documents officiels vérifiables (convocations, procès-verbaux, attestations de dépôt).
 *
 * Chaque document émis reçoit :
 * - un code aléatoire de 80 bits (« K7F2-9QXA-3MTP-W8LD »), imprimé en clair ;
 * - une charge utile canonique (type, code, date d'émission, informations essentielles),
 *   signée par la clé Ed25519 de l'établissement ;
 * - un QR code contenant la charge utile ET la signature : il se vérifie hors ligne avec la seule
 *   clé publique, sans consulter la base.
 * Le registre (table document_emis) permet en plus de savoir si le document est toujours valable :
 * une convocation dont la soutenance a été déplacée ou annulée est signalée « périmée ».
 * La clé privée de l'établissement est chiffrée (AES-256-GCM) par une clé du coffre PKCS#12.
 */
public class VerificationService {

    public static final String PREFIXE = "MTDSI1";
    private static final String DOMAINE = "MemoTDSI-document-v1\n";
    private static final String CLE_PUBLIQUE = "etablissement.cle_publique";
    private static final String CLE_PRIVEE = "etablissement.cle_privee";
    private static final byte[] AAD_CLE = "MemoTDSI-cle-etablissement".getBytes(StandardCharsets.UTF_8);
    private static final int TITRE_MAX = 48;
    private static final DateTimeFormatter EMIS = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
    private static final SecureRandom ALEA = new SecureRandom();

    public enum Type {
        CONVOCATION("Convocation à une soutenance",
                List.of("Destinataire", "Qualité", "Candidat(e)", "Mémoire", "Date et heure", "Salle")),
        PV("Procès-verbal de soutenance",
                List.of("Candidat(e)", "Mémoire", "Niveau / année", "Soutenu le", "Résultat", "Décision")),
        ATTESTATION_DEPOT("Attestation de dépôt de mémoire",
                List.of("Étudiant", "Mémoire", "Niveau / année", "Déposé le", "Empreinte SHA-256 (début)"));

        public final String libelle;
        final List<String> champs;

        Type(String libelle, List<String> champs) {
            this.libelle = libelle;
            this.champs = champs;
        }
    }

    public enum Etat {
        AUTHENTIQUE("Document authentique"),
        PERIME("Document authentique mais plus valable"),
        REVOQUE("Document révoqué"),
        INVALIDE("Document falsifié ou altéré"),
        INCONNU("Code inconnu");

        public final String libelle;

        Etat(String l) {
            libelle = l;
        }
    }

    /** Résultat d'une vérification, prêt à afficher. */
    public record Resultat(Etat etat, String message, String typeLibelle, String code, String emisLe,
            List<String[]> champs, String empreinteCle) { }

    private final DocumentEmisDAO registre;
    private final ParametreDAO parametres;
    private final SoutenanceDAO soutenances;
    private final MemoireDAO memoires;
    private final JournalService journal;

    public VerificationService() {
        this(new DocumentEmisDAOImpl(), new ParametreDAOImpl(), new SoutenanceDAOImpl(), new MemoireDAOImpl(),
                new JournalService());
    }

    public VerificationService(DocumentEmisDAO registre, ParametreDAO parametres, SoutenanceDAO soutenances,
            MemoireDAO memoires, JournalService journal) {
        this.registre = registre;
        this.parametres = parametres;
        this.soutenances = soutenances;
        this.memoires = memoires;
        this.journal = journal;
    }

    // ------------------------------------------------------------------ émission

    /** Convocation (étudiant ou membre du jury) : appelé après le contrôle des droits par ConvocationService. */
    DocumentEmis convocation(Soutenance s, String destinataire, String qualite) throws SQLException {
        Memoire m = s.getMemoire();
        return emettre(Type.CONVOCATION, s.getIdMemoire(), destinataire, etatSoutenance(s), List.of(
                destinataire, qualite, m.getAuteurNomComplet(), titre(m.getTitre()), s.getDateHeureLisible(),
                s.getSalle()));
    }

    /** Procès-verbal d'une soutenance tenue (scolarité ou jury). */
    public DocumentEmis pv(Memoire m, Soutenance s) throws SQLException {
        Utilisateur u = exigerSession();
        if (s == null || s.getStatut() != Soutenance.Statut.TENUE) {
            throw new IllegalStateException("Le procès-verbal n'existe qu'après la soutenance.");
        }
        boolean auteur = m.getAuteur() != null && m.getAuteur().getId() == u.getId();
        if (!auteur && s.roleDe(u.getId()) == null && !SessionManager.aLeRole(com.tdsi.memotheque.model.Role.ADMIN)) {
            throw new SecurityException("Procès-verbal réservé à la scolarité, au jury et au candidat.");
        }
        return emettre(Type.PV, m.getId(), m.getAuteurNomComplet(), etatPv(m), List.of(
                m.getAuteurNomComplet(), titre(m.getTitre()), m.getNiveau() + " " + m.getAnneeAcademique(),
                s.getDateHeureLisible(), Libelles.resultat(m), m.getMention() == null ? "Ajourné" : "Admis"));
    }

    /** Attestation de dépôt (l'étudiant pour son propre mémoire, ou la scolarité). */
    public DocumentEmis attestationDepot(Memoire m) throws SQLException {
        Utilisateur u = exigerSession();
        boolean auteur = m.getAuteur() != null && m.getAuteur().getId() == u.getId();
        if (!auteur && !SessionManager.aLeRole(com.tdsi.memotheque.model.Role.ADMIN)) {
            throw new SecurityException("Attestation réservée à l'auteur du mémoire et à la scolarité.");
        }
        String empreinte = m.getEmpreinteSha256() == null ? "-" : m.getEmpreinteSha256();
        return emettre(Type.ATTESTATION_DEPOT, m.getId(), m.getAuteurNomComplet(), empreinte, List.of(
                m.getAuteurNomComplet(), titre(m.getTitre()), m.getNiveau() + " " + m.getAnneeAcademique(),
                Libelles.date(m.getDateDepot()), empreinte.length() > 16 ? empreinte.substring(0, 16) : empreinte));
    }

    /** Même document, même état : on redonne le même code (réimpression) plutôt que d'en créer un nouveau. */
    private DocumentEmis emettre(Type type, int idRef, String sujet, String etat, List<String> champs) throws SQLException {
        DocumentEmis existant = registre.trouver(type.name(), idRef, sujet, etat);
        if (existant != null) {
            return existant;
        }
        byte[] alea = new byte[10];
        ALEA.nextBytes(alea);
        String code = Totp.base32(alea);
        LocalDateTime maintenant = LocalDateTime.now().withSecond(0).withNano(0);
        List<String> morceaux = new ArrayList<>(List.of(PREFIXE, code, type.name(), maintenant.format(EMIS)));
        for (String c : champs) {
            morceaux.add(propre(c));
        }
        String charge = String.join("|", morceaux);
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer(charge));
        Utilisateur u = SessionManager.getUtilisateur();
        DocumentEmis d = new DocumentEmis(code, type.name(), idRef, sujet, charge, signature, etat, maintenant,
                u == null ? null : u.getId(), false);
        registre.insert(d);
        journal.log(u == null ? null : u.getId(), "DOCUMENT_EMIS", type.name() + " " + d.codeLisible() + " (mémoire #" + idRef + ")");
        return d;
    }

    /** Texte du QR code : charge utile + signature. */
    public static String texteQr(DocumentEmis d) {
        return d.contenu() + "|" + d.signature();
    }

    // ------------------------------------------------------------------ vérification

    /** Accepte le texte lu dans le QR code ou le code imprimé (avec ou sans tirets). Accessible sans connexion. */
    public Resultat verifier(String saisie) throws SQLException {
        String t = saisie == null ? "" : saisie.trim();
        Resultat r = t.startsWith(PREFIXE + "|") ? verifierQr(t) : verifierCode(t);
        Utilisateur u = SessionManager.getUtilisateur();
        journal.log(u == null ? null : u.getId(), "VERIFICATION_DOCUMENT",
                (r.code() == null ? "?" : r.code()) + " : " + r.etat().name());
        return r;
    }

    private Resultat verifierQr(String texte) throws SQLException {
        int i = texte.lastIndexOf('|');
        String charge = texte.substring(0, i);
        String signature = texte.substring(i + 1);
        String[] p = charge.split("\\|", -1);
        Type type = typeDe(p.length > 2 ? p[2] : "");
        String code = p.length > 1 ? p[1] : null;
        if (type == null || p.length != 4 + type.champs.size()) {
            return new Resultat(Etat.INVALIDE, "Ce QR code n'est pas un document MémoTDSI lisible.", null, code, null,
                    List.of(), empreinteCle());
        }
        if (!signatureValide(charge, signature)) {
            return new Resultat(Etat.INVALIDE, "La signature ne correspond pas au contenu : le document a été "
                    + "modifié ou n'a pas été émis par l'établissement.", type.libelle, lisible(code), p[3],
                    List.of(), empreinteCle());
        }
        DocumentEmis d = registre.findByCode(code);
        return conclure(type, code, p, d, d == null ? null : d.contenu().equals(charge));
    }

    private Resultat verifierCode(String saisie) throws SQLException {
        String code = saisie.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z2-7]{16}")) {
            return new Resultat(Etat.INCONNU, "Saisissez le code de 16 caractères imprimé sous le QR code "
                    + "(ex. K7F2-9QXA-3MTP-W8LD), ou collez le texte du QR code.", null, null, null, List.of(), empreinteCle());
        }
        DocumentEmis d = registre.findByCode(code);
        if (d == null) {
            return new Resultat(Etat.INCONNU, "Aucun document n'a été émis avec ce code.", null, lisible(code), null,
                    List.of(), empreinteCle());
        }
        String[] p = d.contenu().split("\\|", -1);
        Type type = typeDe(d.type());
        // La ligne du registre est elle-même contrôlée : une modification directe de la base est détectée.
        if (type == null || !signatureValide(d.contenu(), d.signature())) {
            return new Resultat(Etat.INVALIDE, "L'enregistrement de ce document a été altéré dans la base.",
                    type == null ? null : type.libelle, lisible(code), null, List.of(), empreinteCle());
        }
        return conclure(type, code, p, d, true);
    }

    private Resultat conclure(Type type, String code, String[] p, DocumentEmis d, Boolean identique) throws SQLException {
        List<String[]> champs = new ArrayList<>();
        for (int k = 0; k < type.champs.size(); k++) {
            champs.add(new String[] {type.champs.get(k), p[4 + k]});
        }
        String emis = p[3];
        if (d == null) {
            return new Resultat(Etat.AUTHENTIQUE, "Signature de l'établissement valide. Ce document n'apparaît pas "
                    + "dans le registre de ce poste : son état actuel ne peut pas être confirmé ici.",
                    type.libelle, lisible(code), emis, champs, empreinteCle());
        }
        if (!identique) {
            return new Resultat(Etat.INVALIDE, "Le contenu ne correspond pas au document enregistré sous ce code.",
                    type.libelle, lisible(code), emis, champs, empreinteCle());
        }
        if (d.revoque()) {
            return new Resultat(Etat.REVOQUE, "Ce document a été révoqué par la scolarité.", type.libelle,
                    lisible(code), emis, champs, empreinteCle());
        }
        String actuel = etatActuel(type, d.idReference());
        if (!d.empreinteEtat().equals(actuel)) {
            String pourquoi = switch (type) {
                case CONVOCATION -> "la soutenance a été déplacée, modifiée ou annulée depuis l'émission de cette convocation.";
                case PV -> "le résultat enregistré a changé depuis l'émission de ce procès-verbal.";
                case ATTESTATION_DEPOT -> "le fichier du mémoire a été remplacé depuis l'émission de cette attestation.";
            };
            return new Resultat(Etat.PERIME, "Document authentique, mais " + pourquoi, type.libelle,
                    lisible(code), emis, champs, empreinteCle());
        }
        return new Resultat(Etat.AUTHENTIQUE, "Signature Ed25519 de l'établissement valide et document à jour.",
                type.libelle, lisible(code), emis, champs, empreinteCle());
    }

    private String etatActuel(Type type, int idMemoire) throws SQLException {
        return switch (type) {
            case CONVOCATION -> {
                Soutenance s = soutenances.findByMemoire(idMemoire);
                yield s == null ? "ANNULEE" : etatSoutenance(s);
            }
            case PV -> {
                Memoire m = memoires.findById(idMemoire);
                yield m == null ? "SUPPRIME" : etatPv(m);
            }
            case ATTESTATION_DEPOT -> {
                Memoire m = memoires.findById(idMemoire);
                yield m == null || m.getEmpreinteSha256() == null ? "SUPPRIME" : m.getEmpreinteSha256();
            }
        };
    }

    private static String etatSoutenance(Soutenance s) {
        List<String> jury = new ArrayList<>();
        for (MembreJury j : s.getJury()) {
            jury.add(j.role().name() + ":" + j.enseignant().getId());
        }
        jury.sort(null);
        return s.getStatut() + "|" + s.getDateHeure() + "|" + s.getSalle() + "|" + String.join(",", jury);
    }

    private static String etatPv(Memoire m) {
        return m.getStatut() + "|" + m.getNote() + "|" + m.getMention();
    }

    // ------------------------------------------------------------------ clé de l'établissement

    private byte[] signer(String charge) throws SQLException {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(clePrivee());
            s.update((DOMAINE + charge).getBytes(StandardCharsets.UTF_8));
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature Ed25519 impossible", e);
        }
    }

    private boolean signatureValide(String charge, String signatureB64) throws SQLException {
        try {
            byte[] sig = Base64.getUrlDecoder().decode(signatureB64);
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(clePublique());
            s.update((DOMAINE + charge).getBytes(StandardCharsets.UTF_8));
            return s.verify(sig);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    /** Clé publique de l'établissement, encodée X.509 en Base64 (à publier pour la vérification hors ligne). */
    public String clePubliqueBase64() throws SQLException {
        clePublique();
        return parametres.lire(CLE_PUBLIQUE);
    }

    /** Empreinte courte de la clé publique (SHA-256, 16 premiers caractères hexadécimaux). */
    public String empreinteCle() throws SQLException {
        return SignatureService.empreinte(clePubliqueBase64());
    }

    private PublicKey clePublique() throws SQLException {
        try {
            String b64 = parametres.lire(CLE_PUBLIQUE);
            if (b64 == null) {
                genererCle();
                b64 = parametres.lire(CLE_PUBLIQUE);
            }
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clé publique de l'établissement illisible", e);
        }
    }

    private PrivateKey clePrivee() throws SQLException {
        try {
            String chiffree = parametres.lire(CLE_PRIVEE);
            if (chiffree == null) {
                genererCle();
                chiffree = parametres.lire(CLE_PRIVEE);
            }
            byte[] pkcs8 = CryptoUtil.dechiffrer(GestionCles.cleChiffrementSecrets(), Base64.getDecoder().decode(chiffree), AAD_CLE);
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clé privée de l'établissement illisible", e);
        }
    }

    private synchronized void genererCle() throws SQLException {
        if (parametres.lire(CLE_PUBLIQUE) != null) {
            return;
        }
        try {
            KeyPair paire = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            byte[] chiffree = CryptoUtil.chiffrer(GestionCles.cleChiffrementSecrets(), paire.getPrivate().getEncoded(), AAD_CLE);
            parametres.ecrire(CLE_PRIVEE, Base64.getEncoder().encodeToString(chiffree));
            parametres.ecrire(CLE_PUBLIQUE, Base64.getEncoder().encodeToString(paire.getPublic().getEncoded()));
            journal.log(null, "CLE_ETABLISSEMENT", "Nouvelle clé Ed25519 de l'établissement, empreinte "
                    + SignatureService.empreinte(parametres.lire(CLE_PUBLIQUE)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 indisponible", e);
        }
    }

    // ------------------------------------------------------------------ outils

    private static Utilisateur exigerSession() {
        Utilisateur u = SessionManager.getUtilisateur();
        if (u == null) {
            throw new SecurityException("Connexion requise.");
        }
        return u;
    }

    private static Type typeDe(String nom) {
        for (Type t : Type.values()) {
            if (t.name().equals(nom)) {
                return t;
            }
        }
        return null;
    }

    private static String titre(String t) {
        String s = t == null ? "" : t.strip();
        return s.length() > TITRE_MAX ? s.substring(0, TITRE_MAX - 1).strip() + "…" : s;
    }

    /** Le séparateur « | » est interdit dans les champs ; les retours à la ligne sont aplatis. */
    private static String propre(String s) {
        return s == null ? "-" : s.replace('|', '/').replaceAll("\\s+", " ").strip();
    }

    private static String lisible(String code) {
        return code == null ? null : code.replaceAll("(.{4})(?!$)", "$1-");
    }
}
