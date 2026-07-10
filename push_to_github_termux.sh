#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# PDF Empire — Envoi du projet vers GitHub depuis Termux
# ============================================================
# Prérequis dans Termux :
#   pkg install git
# Authentification : GitHub exige un jeton d'accès personnel
# (Personal Access Token) à la place du mot de passe.
#   -> github.com > Settings > Developer settings >
#      Personal access tokens > Tokens (classic) >
#      Generate new token, coche "repo".
# Quand git demande le mot de passe, colle le jeton.
# ============================================================

set -e

if ! command -v git > /dev/null 2>&1; then
    echo "Erreur : git n'est pas installé. Lance : pkg install git"
    exit 1
fi

echo "== PDF Empire : envoi vers GitHub =="
read -r -p "Ton nom d'utilisateur GitHub : " GH_USER
read -r -p "Nom du dépôt (ex: pdf-pocket-lite) : " GH_REPO

if [ -z "$GH_USER" ] || [ -z "$GH_REPO" ]; then
    echo "Erreur : utilisateur et dépôt obligatoires."
    exit 1
fi

echo ""
echo "IMPORTANT : crée d'abord le dépôt VIDE sur github.com"
echo "(bouton New, nom: $GH_REPO, sans README ni .gitignore)."
read -r -p "Appuie sur Entrée quand le dépôt est créé... " _

if [ ! -d .git ]; then
    git init
    git branch -M main 2> /dev/null || git checkout -b main
fi

if [ -z "$(git config user.email)" ]; then
    read -r -p "Ton courriel pour les commits : " GH_EMAIL
    git config user.email "$GH_EMAIL"
    git config user.name "$GH_USER"
fi

git add -A
git commit -m "PDF Empire - envoi" || echo "Rien de nouveau à valider."

REMOTE_URL="https://github.com/$GH_USER/$GH_REPO.git"
if git remote get-url origin > /dev/null 2>&1; then
    git remote set-url origin "$REMOTE_URL"
else
    git remote add origin "$REMOTE_URL"
fi

echo ""
echo "Envoi vers $REMOTE_URL"
echo "(nom d'utilisateur = $GH_USER, mot de passe = ton JETON GitHub)"
if git push -u origin main; then
    echo ""
    echo "== Terminé =="
    echo "1. Va sur github.com/$GH_USER/$GH_REPO/actions"
    echo "2. Attends la fin du workflow « Build APK » (~5 min)"
    echo "3. Ouvre le run, section Artifacts, télécharge"
    echo "   pdf-pocket-lite-debug-apk, dézippe et installe l'APK."
else
    echo ""
    echo "Échec de l'envoi. Vérifie :"
    echo " - que le dépôt existe et est vide"
    echo " - ton jeton (portée repo) et ton nom d'utilisateur"
    exit 1
fi
