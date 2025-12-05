# Tests d'Intégration FotoShare

Ce répertoire contient les tests d'intégration pour l'application FotoShare.

## 📋 Vue d'ensemble

Les tests d'intégration vérifient le bon fonctionnement de l'application de bout en bout, incluant :
- La persistance en base de données
- Le stockage de fichiers sur disque
- Les interactions entre les couches (Controller → Service → Repository)
- Les comportements de cascade (suppressions en cascade)

## 🧪 Tests Disponibles

### 1. PhotoUploadIntegrationTest
Teste le flux complet d'upload de photos :

#### Tests inclus :
- **testCompletePhotoUploadFlow** : Vérifie le cycle complet d'upload
  - Envoi du fichier via HTTP multipart
  - Création de l'entrée en base de données
  - Enregistrement du fichier sur le disque
  - Génération de la miniature
  
- **testPhotoUploadWithInvalidMimeType** : Vérifie le rejet des fichiers malveillants
  - Détection du vrai type MIME via magic numbers (Apache Tika)
  - Rejet des fichiers qui prétendent être des images mais ne le sont pas
  
- **testPhotoUploadWithOversizedFile** : Vérifie la limite de taille (10 MB)
  
- **testPhotoUploadCreatesUniqueStorageFilename** : Vérifie que les fichiers sont renommés avec UUID
  - Évite les collisions de noms
  - Évite les injections de noms de fichiers

### 2. UserCascadeDeletionIntegrationTest
Teste les suppressions en cascade :

#### Tests inclus :
- **testDeletingUserCascadesDeleteTheirPhotos** : Suppression utilisateur → suppression photos
  
- **testDeletingUserCascadesDeleteTheirComments** : Suppression utilisateur → suppression commentaires
  
- **testDeletingPhotoDoesNotDeleteUser** : La suppression d'une photo ne supprime pas le propriétaire
  
- **testDeletingPhotoCascadesDeleteItsComments** : Suppression photo → suppression commentaires
  
- **testDeletingUserDeletesPhotosAndAllRelatedComments** : Test complet de cascade
  - Suppression utilisateur
  - → Suppression de ses photos
  - → Suppression de tous les commentaires sur ces photos (même ceux d'autres utilisateurs)
  - → Suppression de ses commentaires sur d'autres photos
  
- **testMultipleUserDeletionsWithInterleavedComments** : Scénario complexe avec plusieurs utilisateurs

## 🚀 Exécution des Tests

### Prérequis
- Java 17+
- Maven 3.6+

### Exécuter tous les tests d'intégration
```bash
mvn test -Dtest="*IntegrationTest"
```

### Exécuter un test spécifique
```bash
# Test d'upload
mvn test -Dtest="PhotoUploadIntegrationTest"

# Test de suppression en cascade
mvn test -Dtest="UserCascadeDeletionIntegrationTest"
```

### Exécuter une méthode de test spécifique
```bash
mvn test -Dtest="PhotoUploadIntegrationTest#testCompletePhotoUploadFlow"
```

### Exécuter avec logs détaillés
```bash
mvn test -Dtest="*IntegrationTest" -X
```

## 🔧 Configuration des Tests

### Base de données
Les tests utilisent une base de données H2 en mémoire :
- Mode MySQL compatible
- Création automatique du schéma via `spring.jpa.hibernate.ddl-auto=create-drop`
- Nettoyage automatique après chaque test

Configuration dans `src/test/resources/application-test.properties` :
```properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL
spring.jpa.hibernate.ddl-auto=create-drop
```

### Stockage de fichiers
Les tests stockent les fichiers dans des répertoires temporaires :
- Photos : `target/test-uploads/photos`
- Miniatures : `target/test-uploads/thumbnails`

Les fichiers sont automatiquement nettoyés après chaque test.

### Sécurité
Les tests utilisent `@WithMockUser` pour simuler un utilisateur authentifié :
```java
@WithMockUser(username = "testuser")
```

## 📊 Assertions Vérifiées

### PhotoUploadIntegrationTest
✅ Fichier enregistré sur le disque avec nom UUID  
✅ Miniature générée  
✅ Entrée créée en base de données  
✅ Métadonnées correctes (titre, description, MIME type, etc.)  
✅ Association avec l'utilisateur propriétaire  
✅ Validation MIME type via magic numbers  
✅ Validation taille maximale  
✅ Unicité des noms de fichiers  

### UserCascadeDeletionIntegrationTest
✅ Suppression utilisateur supprime ses photos  
✅ Suppression utilisateur supprime ses commentaires  
✅ Suppression photo supprime ses commentaires  
✅ Suppression photo ne supprime pas le propriétaire  
✅ Suppression utilisateur supprime tous les commentaires sur ses photos (cascade double)  
✅ Les entités non liées ne sont pas affectées  

## 🐛 Debugging

### Activer les logs SQL
Les logs SQL sont déjà activés dans `application-test.properties` :
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
```

### Inspecter la base de données H2 pendant les tests
Ajoutez un breakpoint et utilisez la console H2 :
```properties
spring.h2.console.enabled=true
```
Puis accédez à : http://localhost:8080/h2-console

### Logs applicatifs
```properties
logging.level.com.fotoshare=DEBUG
```

## 🔍 Vérification des Résultats

### Statistiques de tests
```bash
mvn test -Dtest="*IntegrationTest" | grep -A 5 "Tests run"
```

### Rapport de couverture (avec JaCoCo)
```bash
mvn test jacoco:report
# Rapport dans : target/site/jacoco/index.html
```

## 📁 Structure des Tests

```
src/test/java/com/fotoshare/integration/
├── PhotoUploadIntegrationTest.java          # Tests d'upload complet
├── UserCascadeDeletionIntegrationTest.java  # Tests de cascade
└── README.md                                 # Ce fichier

src/test/resources/
└── application-test.properties              # Configuration H2
```

## ✅ Checklist de Validation

Avant de considérer les tests comme réussis, vérifiez :

- [ ] Tous les tests passent (BUILD SUCCESS)
- [ ] Aucune erreur SQL dans les logs
- [ ] Les fichiers temporaires sont nettoyés (vérifier `target/test-uploads/`)
- [ ] Les transactions sont correctement rollbackées
- [ ] Pas de fuite mémoire (vérifier avec un grand nombre d'itérations)

## 🎯 Couverture des Exigences

### Exigence 7.2.1 : Flux complet d'upload
✅ Testé par `PhotoUploadIntegrationTest.testCompletePhotoUploadFlow`
- Envoi fichier → OK
- Vérification présence sur disque → OK
- Vérification entrée en BDD → OK

### Exigence 7.2.2 : Suppression en cascade
✅ Testé par `UserCascadeDeletionIntegrationTest`
- Supprimer un user → ses photos supprimées → OK
- Supprimer un user → ses commentaires supprimés → OK
- Double cascade : user → photos → commentaires sur photos → OK

## 🚨 Problèmes Connus

### Compilation locale avec Lombok
Si vous rencontrez des erreurs de compilation du type "cannot find symbol: method getId()", c'est un problème local de configuration Lombok.

**Solution** : Exécutez les tests via Docker :
```bash
docker compose exec app mvn test -Dtest="*IntegrationTest"
```

Ou vérifiez que l'annotation processor Lombok est activé dans votre IDE.

## 📝 Notes

- Les tests sont annotés avec `@Transactional` pour un rollback automatique
- `@SpringBootTest` charge le contexte Spring complet
- `@AutoConfigureMockMvc` permet de tester les controllers sans serveur HTTP
- Les tests sont indépendants et peuvent s'exécuter dans n'importe quel ordre
- Chaque test nettoie ses propres données (principe d'isolation)

## 🔗 Références

- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Spring Security Test](https://docs.spring.io/spring-security/reference/servlet/test/index.html)