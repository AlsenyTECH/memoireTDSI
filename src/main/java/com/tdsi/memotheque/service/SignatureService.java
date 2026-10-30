package com.tdsi.memotheque.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import com.tdsi.memotheque.dao.CleSignatureDAO;
import com.tdsi.memotheque.dao.MemoireDAO;
import com.tdsi.memotheque.dao.UtilisateurDAO;
import com.tdsi.memotheque.dao.impl.CleSignatureDAOImpl;
import com.tdsi.memotheque.dao.impl.MemoireDAOImpl;
import com.tdsi.memotheque.dao.impl.UtilisateurDAOImpl;
import com.tdsi.memotheque.model.CleSignature;
import com.tdsi.memotheque.model.Memoire;
import com.tdsi.memotheque.model.StatutMemoire;
import com.tdsi.memotheque.model.Utilisateur;
import com.tdsi.memotheque.util.CryptoUtil;
import com.tdsi.memotheque.util.PasswordUtil;

/**
 * Signature numérique Ed25519 des mémoires validés (authenticité + non-répudiation).
 *
 * Message signé (encodage canonique longueur:valeur, voir CryptoUtil.encoder) :
 *   "MemoTDSI-signature-v1", id, SHA-256 du PDF, titre, id auteur, année, niveau,
 *   id validateur, date de publication.
 * Modifier l'un de ces éléments en base (ou remplacer le PDF) rend la signature invalide.
 *
 * Clé privée : PKCS#8 chiffré AES-256-GCM, clé = PBKDF2(mot de passe du validateur, sel, n).
 * Ni l'application ni l'admin ne peuvent signer à la place du validateur sans son mot de passe.
 */
public class SignatureService {

    public static final String ALGORITHME = "Ed25519";
    private static final String DOMAINE = "MemoTDSI-signature-v1";
    private static final String DOMAINE_V2 = "MemoTDSI-pv-v2";
    private static final String PREFIXE_V2 = "v2:";

    public enum Etat { VALIDE, INVALIDE, ABSENTE }

    /** Résultat affiché sur la fiche d'un mémoire. */
    public record Verification(Etat etat, String message, String signataire, String empreinteCle) { }

    /** Signature produite lors d'une validation. */
    public record SignatureCalculee(String signature, int idCle, String empreinteCle) { }

    private final CleSignatureDAO cleDAO;
    private final UtilisateurDAO utilisateurDAO;
    private final MemoireDAO memoireDAO;

    public SignatureService() {
        this(new CleSignatureDAOImpl(), new UtilisateurDAOImpl(), new MemoireDAOImpl());
    }

    public SignatureService(CleSignatureDAO cleDAO, UtilisateurDAO utilisateurDAO, MemoireDAO memoireDAO) {
        this.cleDAO = Objects.requireNonNull(cleDAO);
        this.utilisateurDAO = Objects.requireNonNull(utilisateurDAO);
        this.memoireDAO = Objects.requireNonNull(memoireDAO);
    }

    // ------------------------------------------------------------------ signature

    /**
     * Signe la validation d'un mémoire. Le mot de passe du validateur est re-vérifié
     * (ré-authentification pour une action engageante) puis sert à déchiffrer sa clé privée.
     * Si le validateur n'a pas encore de clé, elle est créée.
     */
    public SignatureCalculee signer(Utilisateur validateur, String motDePasse, Memoire m,
            LocalDate datePublication) throws SQLException {

        Utilisateur enBase = utilisateurDAO.findById(validateur.getId());
        if (enBase == null || !PasswordUtil.verifier(motDePasse, enBase.getSel(), enBase.getMotDePasseHash())) {
            throw new SecurityException("Mot de passe incorrect : validation non signée.");
        }

        CleSignature cle = cleDAO.findActive(validateur.getId());
        PrivateKey privee = null;
        if (cle != null) {
            try {
                privee = dechiffrerClePrivee(cle, motDePasse);
            } catch (SecurityException e) {
                // Clé chiffrée avec un ancien mot de passe (le compte, lui, est authentifié) :
                // elle est révoquée et remplacée. Les anciennes signatures restent vérifiables.
                cleDAO.desactiver(validateur.getId());
                cle = null;
            }
        }
        if (cle == null) {
            KeyPair paire = genererPaire();
            cle = enregistrerNouvelleCle(validateur.getId(), paire, motDePasse);
            privee = paire.getPrivate();
        }

        // Format v2 : la signature couvre aussi le PV (mention, note, date de soutenance).
        byte[] message = messageV2(m, validateur.getId(), datePublication);

        try {
            Signature s = Signature.getInstance(ALGORITHME);
            s.initSign(privee);
            s.update(message);
            return new SignatureCalculee(PREFIXE_V2 + Base64.getEncoder().encodeToString(s.sign()), cle.getId(),
                    empreinte(cle.getClePublique()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signature Ed25519 impossible", e);
        }
    }

    // ------------------------------------------------------------------ vérification

    /** Vérifie la signature d'un mémoire, relu en base (on ne fait pas confiance à l'objet affiché). */
    public Verification verifier(int idMemoire) throws SQLException {
        Memoire m = memoireDAO.findById(idMemoire);
        if (m == null) {
            throw new IllegalArgumentException("Mémoire introuvable.");
        }
        if (m.getSignature() == null || m.getIdCleSignature() == null) {
            String raison = m.getStatut() == StatutMemoire.VALIDE
                    ? "Mémoire validé avant l'activation des signatures : pas de signature numérique."
                    : "Mémoire non encore validé : pas de signature.";
            return new Verification(Etat.ABSENTE, raison, null, null);
        }

        CleSignature cle = cleDAO.findById(m.getIdCleSignature());
        if (cle == null || m.getValidateur() == null || m.getDatePublication() == null
                || cle.getIdUtilisateur() != m.getValidateur().getId()) {
            return new Verification(Etat.INVALIDE,
                    "Signature invalide : la clé ne correspond pas au validateur enregistré.", null, null);
        }

        Utilisateur signataire = utilisateurDAO.findById(cle.getIdUtilisateur());
        String nom = signataire == null ? "Utilisateur #" + cle.getIdUtilisateur() : signataire.getNomComplet();
        String empreinte = empreinte(cle.getClePublique());

        boolean v2 = m.getSignature().startsWith(PREFIXE_V2);
        byte[] message = v2
                ? messageV2(m, m.getValidateur().getId(), m.getDatePublication())
                : message(m.getId(), m.getEmpreinteSha256(), m.getTitre(),
                        m.getAuteur() == null ? 0 : m.getAuteur().getId(), m.getAnneeAcademique(),
                        m.getNiveau() == null ? null : m.getNiveau().name(), m.getValidateur().getId(),
                        m.getDatePublication());
        String brute = v2 ? m.getSignature().substring(PREFIXE_V2.length()) : m.getSignature();

        boolean ok;
        try {
            PublicKey publique = KeyFactory.getInstance(ALGORITHME)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(cle.getClePublique())));
            Signature s = Signature.getInstance(ALGORITHME);
            s.initVerify(publique);
            s.update(message);
            ok = s.verify(Base64.getDecoder().decode(brute));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            ok = false;
        }

        if (!ok) {
            return new Verification(Etat.INVALIDE,
                    "Signature INVALIDE : les informations, le PV ou le PDF ont été modifiés après la publication.",
                    nom, empreinte);
        }
        return new Verification(Etat.VALIDE, "Signature Ed25519 valide : "
                + (v2 ? "procès-verbal signé par " : "validé par ") + nom + " le "
                + m.getDatePublication().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " (clé " + empreinte + ").", nom, empreinte);
    }

    // ------------------------------------------------------------------ cycle de vie des clés

    /** Changement de mot de passe : la clé privée est re-chiffrée avec le nouveau mot de passe. */
    public void rechiffrer(int idUtilisateur, String ancien, String nouveau) throws SQLException {
        CleSignature cle = cleDAO.findActive(idUtilisateur);
        if (cle == null) {
            return;
        }
        PrivateKey privee;
        try {
            privee = dechiffrerClePrivee(cle, ancien);
        } catch (SecurityException e) {
            // Clé illisible avec l'ancien mot de passe : elle est révoquée, une nouvelle sera créée.
            cleDAO.desactiver(idUtilisateur);
            return;
        }
        String sel = PasswordUtil.genererSel();
        cleDAO.changerChiffrement(cle.getId(), chiffrerClePrivee(privee, nouveau, sel, idUtilisateur), sel,
                PasswordUtil.ITERATIONS);
    }

    /** Mot de passe réinitialisé par l'admin : l'ancienne clé privée devient inutilisable, on la désactive. */
    public void revoquer(int idUtilisateur) throws SQLException {
        cleDAO.desactiver(idUtilisateur);
    }

    /** Empreinte lisible d'une clé publique : 16 premiers caractères hex du SHA-256, par groupes de 4. */
    public static String empreinte(String clePubliqueBase64) {
        String hex = CryptoUtil.sha256Hex(Base64.getDecoder().decode(clePubliqueBase64)).substring(0, 16);
        return hex.replaceAll("(.{4})(?!$)", "$1:");
    }

    // ------------------------------------------------------------------ outils

    /** Message v2 : identité du mémoire + PDF + procès-verbal de soutenance + signataire + date. */
    static byte[] messageV2(Memoire m, int idSignataire, LocalDate datePublication) {
        return CryptoUtil.encoder(DOMAINE_V2, Integer.toString(m.getId()), m.getEmpreinteSha256(), m.getTitre(),
                Integer.toString(m.getAuteur() == null ? 0 : m.getAuteur().getId()), m.getAnneeAcademique(),
                m.getNiveau() == null ? null : m.getNiveau().name(),
                m.getMention() == null ? null : m.getMention().name(),
                m.getNote() == null ? null : String.format(java.util.Locale.ROOT, "%.2f", m.getNote()),
                m.getDateSoutenance() == null ? null : m.getDateSoutenance().toString(),
                Integer.toString(idSignataire), datePublication == null ? null : datePublication.toString());
    }

    /** Message v1 (signatures produites avant le circuit de soutenance). */
    static byte[] message(int idMemoire, String sha256, String titre, int idAuteur, String annee,
            String niveau, int idValidateur, LocalDate datePublication) {
        return CryptoUtil.encoder(DOMAINE, Integer.toString(idMemoire), sha256, titre,
                Integer.toString(idAuteur), annee, niveau, Integer.toString(idValidateur),
                datePublication == null ? null : datePublication.toString());
    }

    private KeyPair genererPaire() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHME).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 indisponible (JDK 15 ou plus requis)", e);
        }
    }

    private CleSignature enregistrerNouvelleCle(int idUtilisateur, KeyPair paire, String motDePasse)
            throws SQLException {
        String sel = PasswordUtil.genererSel();
        CleSignature cle = new CleSignature();
        cle.setIdUtilisateur(idUtilisateur);
        cle.setClePublique(Base64.getEncoder().encodeToString(paire.getPublic().getEncoded()));
        cle.setClePriveeChiffree(chiffrerClePrivee(paire.getPrivate(), motDePasse, sel, idUtilisateur));
        cle.setSel(sel);
        cle.setIterations(PasswordUtil.ITERATIONS);
        cleDAO.insert(cle);
        return cle;
    }

    private String chiffrerClePrivee(PrivateKey privee, String motDePasse, String sel, int idUtilisateur) {
        byte[] cleAes = PasswordUtil.deriver(motDePasse.toCharArray(), Base64.getDecoder().decode(sel),
                PasswordUtil.ITERATIONS, 256);
        byte[] pkcs8 = privee.getEncoded();
        try {
            return Base64.getEncoder().encodeToString(
                    CryptoUtil.chiffrer(CryptoUtil.cleAes(cleAes), pkcs8, donneesAssociees(idUtilisateur)));
        } finally {
            Arrays.fill(cleAes, (byte) 0);
            Arrays.fill(pkcs8, (byte) 0);
        }
    }

    private PrivateKey dechiffrerClePrivee(CleSignature cle, String motDePasse) {
        byte[] cleAes = PasswordUtil.deriver(motDePasse.toCharArray(), Base64.getDecoder().decode(cle.getSel()),
                cle.getIterations(), 256);
        byte[] pkcs8 = null;
        try {
            pkcs8 = CryptoUtil.dechiffrer(CryptoUtil.cleAes(cleAes),
                    Base64.getDecoder().decode(cle.getClePriveeChiffree()), donneesAssociees(cle.getIdUtilisateur()));
            return KeyFactory.getInstance(ALGORITHME).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Clé privée Ed25519 illisible", e);
        } finally {
            Arrays.fill(cleAes, (byte) 0);
            if (pkcs8 != null) {
                Arrays.fill(pkcs8, (byte) 0);
            }
        }
    }

    private static byte[] donneesAssociees(int idUtilisateur) {
        return ("MemoTDSI-cle-signature|" + idUtilisateur).getBytes(StandardCharsets.UTF_8);
    }
}
