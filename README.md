# FotoShare - Application de Partage de Photos Sécurisée

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)
![Java](https://img.shields.io/badge/Java-17-blue)
![MariaDB](https://img.shields.io/badge/MariaDB-11.2-blue)
![Docker](https://img.shields.io/badge/Docker-Ready-blue)

Une application web de partage de photos sécurisée construite avec Spring Boot, Thymeleaf et MariaDB, suivant une architecture N-Tiers stricte.

## 📋 Table des matières

- [Fonctionnalités](#-fonctionnalités)
- [Architecture](#-architecture)
- [Prérequis](#-prérequis)
- [Déploiement avec Docker](#-déploiement-avec-docker)
- [Développement local](#-développement-local)
- [Configuration](#-configuration)
- [Sécurité](#-sécurité)
- [API et Endpoints](#-api-et-endpoints)
- [Tests](#-tests)
- [Structure du projet](#-structure-du-projet)

## 🚀 Fonctionnalités

### Gestion des utilisateurs
- ✅ Inscription avec validation stricte (email, complexité mot de passe)
- ✅ Authentification via Spring Security
- ✅ Gestion des rôles (USER, ADMIN, MODERATOR)
- ✅ Activation/désactivation des comptes

### Gestion des photos
- ✅ Upload sécurisé avec validation MIME (Magic Numbers via Apache Tika)
- ✅ Génération automatique de miniatures
- ✅ Stockage sécurisé avec renommage UUID
- ✅ Contrôle de visibilité (Public/Privé)

### Partage et permissions
- ✅ Système ACL avec 3 niveaux de permission (READ, COMMENT, ADMIN)
- ✅ Partage granulaire avec utilisateurs spécifiques
- ✅ Commentaires sur les photos

### Albums
- ✅ Création et gestion d'albums
- ✅ Organisation des photos

## 🏗 Architecture

L'application suit une architecture **N-Tiers** stricte :

```
┌─────────────────────────────────────────────────────────────┐
│                    TIER 1: WEB (Nginx)                      │
│                    - Reverse Proxy                          │
│                    - SSL Termination                        │
│                    - Static Files Caching                   │
│                    - Rate Limiting                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                TIER 2: APPLICATION (Spring Boot)            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ Presentation│  │   Service   │  │    Persistence      │ │
│  │   Layer     │  │   Layer     │  │      Layer          │ │
│  │ Controllers │→ │  Services   │→ │   Repositories      │ │
│  │    DTOs     │  │ Transactions│  │   JPA Entities      │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   TIER 3: DATA (MariaDB)                    │
│                    - Données structurées                    │
│                    - Relations                              │
└─────────────────────────────────────────────────────────────┘
```

## 📋 Prérequis

### Pour Docker (recommandé)
- Docker 20.10+
- Docker Compose 2.0+

### Pour développement local
- Java 17+
- Maven 3.8+
- MariaDB 11.2+ (ou MySQL 8.0+)

## 🐳 Déploiement avec Docker

### 1. Cloner le projet

```bash
git clone <repository-url>
cd SpringBootDataJPA
```

### 2. Configuration des variables d'environnement

Créez un fichier `.env` à la racine du projet :

```bash
# Base de données
DB_ROOT_PASSWORD=your_secure_root_password
DB_USER=fotoshare
DB_PASSWORD=your_secure_password

# Application
APP_PORT=80
```

### 3. Lancer l'application

```bash
# Build et lancement de tous les services
docker-compose up -d --build

# Vérifier les logs
docker-compose logs -f

# Vérifier l'état des services
docker-compose ps
```

### 4. Accéder à l'application

- **Application** : http://localhost
- **Compte admin par défaut** :
  - Username: `admin`
  - Password: `Admin123!`

### Commandes Docker utiles

```bash
# Arrêter tous les services
docker-compose down

# Arrêter et supprimer les volumes (reset complet)
docker-compose down -v

# Reconstruire un service spécifique
docker-compose build app

# Voir les logs d'un service
docker-compose logs -f app

# Exécuter une commande dans un conteneur
docker-compose exec db mariadb -u fotoshare -p fotoshareDB
```

## 💻 Développement local

### 1. Configurer MariaDB

```sql
CREATE DATABASE IF NOT EXISTS fotoshareDB;
CREATE USER 'fotoshare'@'localhost' IDENTIFIED BY 'fotosharepass';
GRANT ALL PRIVILEGES ON fotoshareDB.* TO 'fotoshare'@'localhost';
FLUSH PRIVILEGES;

-- Exécuter le script d'initialisation
SOURCE docker/db/init.sql;
```

### 2. Lancer l'application

```bash
# Avec Maven
mvn spring-boot:run

# Ou compiler et exécuter
mvn clean package -DskipTests
java -jar target/fotoshare-1.0.0.jar
```

### 3. Accéder à l'application

- **Application** : http://localhost:8080

## ⚙️ Configuration

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `SPRING_DATASOURCE_URL` | URL JDBC de la base de données | `jdbc:mariadb://db:3306/fotoshareDB` |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur base de données | `fotoshare` |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe base de données | `fotosharepass` |
| `FOTOSHARE_UPLOAD_PATH` | Chemin de stockage des photos | `/app/uploads/photos` |
| `FOTOSHARE_THUMBNAIL_PATH` | Chemin de stockage des miniatures | `/app/uploads/thumbnails` |

### Limites de fichiers

- **Taille maximale** : 10 MB par fichier
- **Types acceptés** : JPEG, PNG, GIF, WebP
- **Validation** : Via Magic Numbers (Apache Tika)

## 🔐 Sécurité

### Mesures implémentées

1. **Authentification**
   - Hachage BCrypt des mots de passe
   - Protection contre les attaques par force brute
   - Session management sécurisé

2. **Autorisation**
   - RBAC (Role-Based Access Control)
   - ACL (Access Control List) pour les photos
   - Vérification des permissions via `@PreAuthorize`

3. **Protection des données**
   - CSRF activé par défaut
   - XSS : échappement automatique via Thymeleaf
   - SQL Injection : prévention via JPA

4. **Upload sécurisé**
   - Validation MIME via Magic Numbers
   - Renommage UUID des fichiers
   - Stockage hors racine web

### Règles de protection URL

| Pattern | Accès |
|---------|-------|
| `/css/**`, `/js/**`, `/images/**` | Public |
| `/login`, `/register` | Public |
| `/gallery` | Public |
| `/admin/**` | ADMIN uniquement |
| `/moderator/**` | ADMIN, MODERATOR |
| `/**` (autres) | Authentifié |

## 📡 API et Endpoints

### Authentification
- `GET /login` - Page de connexion
- `POST /login` - Traitement de connexion
- `GET /register` - Page d'inscription
- `POST /register` - Traitement d'inscription
- `POST /logout` - Déconnexion

### Photos
- `GET /photos/my` - Mes photos
- `GET /photos/shared` - Photos partagées avec moi
- `GET /photos/upload` - Formulaire d'upload
- `POST /photos/upload` - Traitement d'upload
- `GET /photos/view/{id}` - Voir une photo
- `GET /photos/view/{id}/image` - Image originale
- `GET /photos/view/{id}/thumbnail` - Miniature
- `POST /photos/edit/{id}` - Modifier une photo
- `POST /photos/delete/{id}` - Supprimer une photo
- `POST /photos/share/{id}` - Partager une photo
- `POST /photos/comment/{id}` - Commenter une photo

### Albums
- `GET /albums` - Liste des albums
- `GET /albums/create` - Formulaire de création
- `POST /albums/create` - Créer un album
- `GET /albums/view/{id}` - Voir un album
- `POST /albums/delete/{id}` - Supprimer un album

### Administration
- `GET /admin` - Dashboard admin
- `GET /admin/users` - Gestion des utilisateurs
- `POST /admin/users/{id}/toggle-status` - Activer/désactiver un compte
- `POST /admin/users/{id}/role` - Changer le rôle

## 🧪 Tests

### Exécuter les tests

```bash
# Tous les tests
mvn test

# Tests avec rapport de couverture
mvn test jacoco:report

# Tests d'intégration
mvn verify
```

### Types de tests

- **Tests unitaires** : Mappers, SecurityService
- **Tests d'intégration** : Upload de fichiers, cascade de suppression

## 📁 Structure du projet

```
SpringBootDataJPA/
├── docker/
│   ├── app/
│   │   └── Dockerfile          # Image Spring Boot
│   ├── db/
│   │   ├── Dockerfile          # Image MariaDB
│   │   └── init.sql            # Script d'initialisation
│   └── nginx/
│       ├── Dockerfile          # Image Nginx
│       └── nginx.conf          # Configuration reverse proxy
├── src/main/java/com/fotoshare/
│   ├── config/                 # Configuration Spring
│   ├── controller/             # Controllers (Présentation)
│   ├── dto/                    # Data Transfer Objects
│   ├── entity/                 # Entités JPA
│   ├── enums/                  # Énumérations
│   ├── mapper/                 # Mappers Entité <-> DTO
│   ├── repository/             # Repositories Spring Data
│   └── service/                # Services (Métier)
├── src/main/resources/
│   ├── static/css/             # Styles CSS
│   ├── templates/              # Templates Thymeleaf
│   ├── application.properties  # Configuration par défaut
│   └── application-docker.properties  # Configuration Docker
├── docker-compose.yml          # Orchestration Docker
├── pom.xml                     # Dépendances Maven
└── README.md                   # Ce fichier
```

## 📊 Schéma de base de données

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  utilisateur │     │    photo     │     │    album     │
├──────────────┤     ├──────────────┤     ├──────────────┤
│ id           │──┐  │ id           │  ┌──│ id           │
│ username     │  │  │ title        │  │  │ name         │
│ email        │  │  │ description  │  │  │ description  │
│ password_hash│  └─→│ owner_id     │←─┘  │ owner_id     │
│ role         │     │ visibility   │     └──────────────┘
│ enabled      │     │ storage_file │            ↑
└──────────────┘     │ content_type │     ┌──────┴───────┐
       ↑             └──────────────┘     │ album_photo  │
       │                    ↑             ├──────────────┤
       │              ┌─────┴─────┐       │ album_id     │
       │              │  partage  │       │ photo_id     │
       │              ├───────────┤       └──────────────┘
       └──────────────│ user_id   │
                      │ photo_id  │
                      │ permission│
                      └───────────┘
                            ↑
                      ┌─────┴─────┐
                      │commentaire│
                      ├───────────┤
                      │ photo_id  │
                      │ author_id │
                      │ text      │
                      └───────────┘
```

## 🤝 Contribution

1. Fork le projet
2. Créer une branche (`git checkout -b feature/AmazingFeature`)
3. Commit les changements (`git commit -m 'Add AmazingFeature'`)
4. Push sur la branche (`git push origin feature/AmazingFeature`)
5. Ouvrir une Pull Request

## 📄 Licence

Ce projet est sous licence MIT. Voir le fichier `LICENSE` pour plus de détails.

## 👥 Auteurs

- **FotoShare Team**

---

<p align="center">
  <i>FotoShare - Partagez vos photos en toute sécurité 📸</i>
</p>