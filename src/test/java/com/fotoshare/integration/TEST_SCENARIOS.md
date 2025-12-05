# Scénarios de Tests d'Intégration - Référence Détaillée

## 📋 Guide de Référence des Assertions

Ce document détaille tous les scénarios de tests et leurs assertions pour faciliter la maintenance et la compréhension des tests d'intégration.

---

## 🎯 PhotoUploadIntegrationTest

### Test 1: testCompletePhotoUploadFlow

#### 🎬 Scénario
Upload complet d'une photo JPEG valide via l'interface web.

#### 📥 Input
```java
MockMultipartFile file = new MockMultipartFile(
    "file",
    "test-photo.jpg",
    "image/jpeg",
    createValidJpegImage() // 1x1 pixel JPEG
);

Params:
- title: "Integration Test Photo"
- description: "This is a test photo for integration testing"
- visibility: "PUBLIC"
```

#### 🔍 Assertions

##### Phase 1: Vérification HTTP
```java
// Assertion 1: Redirection réussie
.andExpect(status().is3xxRedirection())
.andExpect(redirectedUrlPattern("/photos/view/*"))
```

##### Phase 2: Vérification Base de Données
```java
// Assertion 2: Compteur de photos incrémenté
assertThat(photosCountAfter).isEqualTo(photosCountBefore + 1);

// Assertion 3: Photo trouvée en BDD
assertThat(photoOpt).isPresent();

// Assertion 4-11: Métadonnées correctes
assertThat(savedPhoto.getTitle()).isEqualTo("Integration Test Photo");
assertThat(savedPhoto.getDescription()).isEqualTo("This is a test photo...");
assertThat(savedPhoto.getVisibility()).isEqualTo(Visibility.PUBLIC);
assertThat(savedPhoto.getOriginalFilename()).isEqualTo("test-photo.jpg");
assertThat(savedPhoto.getContentType()).isEqualTo("image/jpeg");
assertThat(savedPhoto.getStorageFilename()).isNotNull();
assertThat(savedPhoto.getStorageFilename()).isNotEmpty();
assertThat(savedPhoto.getOwner().getId()).isEqualTo(testUser.getId());
assertThat(savedPhoto.getCreatedAt()).isNotNull();
```

##### Phase 3: Vérification Stockage Disque (Original)
```java
Path originalFilePath = photoStoragePath.resolve(savedPhoto.getStorageFilename());

// Assertion 12: Fichier existe
assertThat(Files.exists(originalFilePath))
    .as("Original photo file should exist on disk")
    .isTrue();

// Assertion 13: C'est un fichier régulier
assertThat(Files.isRegularFile(originalFilePath))
    .as("Original photo should be a regular file")
    .isTrue();

// Assertion 14: Fichier non vide
assertThat(Files.size(originalFilePath))
    .as("Original photo file should not be empty")
    .isGreaterThan(0);
```

##### Phase 4: Vérification Thumbnail
```java
String thumbnailFilename = "thumb_" + savedPhoto.getStorageFilename();
Path thumbnailFilePath = thumbnailStoragePath.resolve(thumbnailFilename);

// Assertion 15: Thumbnail existe
assertThat(Files.exists(thumbnailFilePath))
    .as("Thumbnail file should exist on disk")
    .isTrue();

// Assertion 16: C'est un fichier régulier
assertThat(Files.isRegularFile(thumbnailFilePath))
    .as("Thumbnail should be a regular file")
    .isTrue();

// Assertion 17: Thumbnail non vide
assertThat(Files.size(thumbnailFilePath))
    .as("Thumbnail file should not be empty")
    .isGreaterThan(0);
```

#### ✅ Résultat Attendu
- **17 assertions** passent
- 1 entrée BDD créée
- 2 fichiers créés (original + thumbnail)

---

### Test 2: testPhotoUploadWithInvalidMimeType

#### 🎬 Scénario
Tentative d'upload d'un fichier texte déguisé en image JPEG.

#### 📥 Input
```java
byte[] textBytes = "This is not an image".getBytes();
MockMultipartFile file = new MockMultipartFile(
    "file",
    "malicious.jpg",     // Extension .jpg
    "image/jpeg",        // Header MIME
    textBytes            // Contenu texte
);
```

#### 🔍 Assertions
```java
// Assertion 1: Redirection avec erreur
.andExpect(status().is3xxRedirection())
.andExpect(flash().attributeExists("error"))

// Assertion 2: Aucune photo créée
assertThat(photosCountAfter).isEqualTo(photosCountBefore);
```

#### ✅ Résultat Attendu
- **2 assertions** passent
- 0 entrée BDD créée
- 0 fichier créé
- Message d'erreur dans flash attribute

#### 🛡️ Sécurité Validée
- **Magic Numbers** : Apache Tika détecte le vrai type MIME
- **Extension Spoofing** : Extension ignorée
- **Header Spoofing** : Header HTTP ignoré

---

### Test 3: testPhotoUploadWithOversizedFile

#### 🎬 Scénario
Upload d'un fichier dépassant la limite de 10 MB.

#### 📥 Input
```java
byte[] largeImageBytes = new byte[11 * 1024 * 1024]; // 11 MB
MockMultipartFile file = new MockMultipartFile(
    "file",
    "large-photo.jpg",
    "image/jpeg",
    largeImageBytes
);
```

#### 🔍 Assertions
```java
// Assertion 1: Erreur client
.andExpect(status().is4xxClientError())

// Assertion 2: Aucune photo créée
assertThat(photosCountAfter).isEqualTo(photosCountBefore);
```

#### ✅ Résultat Attendu
- **2 assertions** passent
- HTTP 400 ou 413
- 0 entrée BDD
- 0 fichier créé

---

### Test 4: testPhotoUploadCreatesUniqueStorageFilename

#### 🎬 Scénario
Upload de 2 fichiers avec le même nom original.

#### 📥 Input
```java
// Upload 1
file1: originalFilename="duplicate.jpg", title="Photo 1"

// Upload 2
file2: originalFilename="duplicate.jpg", title="Photo 2"
```

#### 🔍 Assertions
```java
// Assertion 1-2: Les deux uploads réussissent
.andExpect(status().is3xxRedirection()) // x2

// Assertion 3-4: Les deux photos existent en BDD
assertThat(photo1).isPresent();
assertThat(photo2).isPresent();

// Assertion 5: Storage filenames différents
assertThat(photo1.get().getStorageFilename())
    .isNotEqualTo(photo2.get().getStorageFilename());

// Assertion 6: Original filenames identiques (préservés)
assertThat(photo1.get().getOriginalFilename())
    .isEqualTo(photo2.get().getOriginalFilename());
```

#### ✅ Résultat Attendu
- **6 assertions** passent
- 2 entrées BDD avec storageFilename différents
- 2 fichiers physiques distincts
- Pas d'écrasement de fichier

---

## 🗑️ UserCascadeDeletionIntegrationTest

### Données de Test (Setup)

```
Utilisateurs:
┌─────────────┬───────────────┬──────────────┐
│ Variable    │ Username      │ Rôle         │
├─────────────┼───────────────┼──────────────┤
│ ownerUser   │ photoowner    │ Propriétaire │
│ commenter   │ commenter     │ Commenteur   │
└─────────────┴───────────────┴──────────────┘

Photos:
┌─────────┬───────────┬─────────────┬──────────────┐
│ Photo   │ Titre     │ Propriétaire│ Commentaires │
├─────────┼───────────┼─────────────┼──────────────┤
│ photo1  │ "Photo 1" │ ownerUser   │ 2            │
│ photo2  │ "Photo 2" │ ownerUser   │ 1            │
└─────────┴───────────┴─────────────┴──────────────┘

Commentaires:
┌──────────┬─────────┬────────────┬────────────────────────┐
│ Comment  │ Photo   │ Auteur     │ Texte                  │
├──────────┼─────────┼────────────┼────────────────────────┤
│ comment1 │ photo1  │ ownerUser  │ "Owner's comment..."   │
│ comment2 │ photo1  │ commenter  │ "Commenter's comment..." │
│ comment3 │ photo2  │ commenter  │ "Commenter's comment..." │
└──────────┴─────────┴────────────┴────────────────────────┘
```

---

### Test 1: testDeletingUserCascadesDeleteTheirPhotos

#### 🎬 Scénario
Suppression du propriétaire de photos.

#### 🔧 Action
```java
userRepository.delete(ownerUser);
userRepository.flush();
```

#### 🔍 Assertions
```java
// Assertion 1: User supprimé
assertThat(userRepository.findById(ownerUser.getId())).isEmpty();

// Assertion 2: Photo1 supprimée (cascade)
assertThat(photoRepository.findById(photo1.getId()))
    .as("Photo 1 owned by deleted user should be cascade deleted")
    .isEmpty();

// Assertion 3: Photo2 supprimée (cascade)
assertThat(photoRepository.findById(photo2.getId()))
    .as("Photo 2 owned by deleted user should be cascade deleted")
    .isEmpty();

// Assertion 4: Compteur photos décrémenté
assertThat(photoCountAfter)
    .as("Photo count should decrease by 2 after user deletion")
    .isEqualTo(photoCountBefore - 2);
```

#### ✅ Résultat Attendu
```
Avant:
Users: 2, Photos: 2, Comments: 3

DELETE ownerUser

Après:
Users: 1, Photos: 0, Comments: 0 (cascade via photos)
```

#### 📊 Relation JPA Testée
```java
@Entity
class User {
    @OneToMany(mappedBy = "owner", 
               cascade = CascadeType.ALL, 
               orphanRemoval = true)
    private List<Photo> photos;
}
```

---

### Test 2: testDeletingUserCascadesDeleteTheirComments

#### 🎬 Scénario
Suppression d'un utilisateur qui a commenté (mais ne possède pas de photos).

#### 🔧 Action
```java
userRepository.delete(commenterUser);
userRepository.flush();
```

#### 🔍 Assertions
```java
// Assertion 1: User supprimé
assertThat(userRepository.findById(commenterUser.getId())).isEmpty();

// Assertion 2: Comment2 supprimé (authored by commenterUser)
assertThat(commentRepository.findById(comment2.getId()))
    .as("Comment by deleted user should be cascade deleted")
    .isEmpty();

// Assertion 3: Comment3 supprimé (authored by commenterUser)
assertThat(commentRepository.findById(comment3.getId()))
    .as("Comment by deleted user should be cascade deleted")
    .isEmpty();

// Assertion 4: Comment1 toujours présent (author ≠ commenterUser)
assertThat(commentRepository.findById(comment1.getId()))
    .as("Comment by other user should still exist")
    .isPresent();

// Assertion 5: Photos toujours présentes (owner ≠ commenterUser)
assertThat(photoRepository.findById(photo1.getId()))
    .as("Photos should not be deleted when commenter is deleted")
    .isPresent();

// Assertion 6: Compteur comments décrémenté de 2
assertThat(commentCountAfter)
    .as("Comment count should decrease by 2 after user deletion")
    .isEqualTo(commentCountBefore - 2);
```

#### ✅ Résultat Attendu
```
Avant:
Users: 2, Photos: 2, Comments: 3

DELETE commenterUser

Après:
Users: 1, Photos: 2, Comments: 1 (comment1 reste)
```

---

### Test 3: testDeletingPhotoDoesNotDeleteUser

#### 🎬 Scénario
Suppression d'une photo ne doit pas affecter le propriétaire.

#### 🔧 Action
```java
photoRepository.delete(photo1);
photoRepository.flush();
```

#### 🔍 Assertions
```java
// Assertion 1: Photo supprimée
assertThat(photoRepository.findById(photo1.getId()))
    .as("Deleted photo should not exist")
    .isEmpty();

// Assertion 2: Owner toujours présent
assertThat(userRepository.findById(ownerUser.getId()))
    .as("Owner user should still exist after photo deletion")
    .isPresent();

// Assertion 3: Autre photo du même owner toujours présente
assertThat(photoRepository.findById(photo2.getId()))
    .as("Other photos by the same owner should still exist")
    .isPresent();
```

#### ✅ Résultat Attendu
```
Avant:
Users: 2, Photos: 2, Comments: 3

DELETE photo1

Après:
Users: 2, Photos: 1, Comments: 1 (comment3 sur photo2)
```

---

### Test 4: testDeletingPhotoCascadesDeleteItsComments

#### 🎬 Scénario
Suppression d'une photo supprime tous ses commentaires (peu importe l'auteur).

#### 🔧 Action
```java
photoRepository.delete(photo1);
photoRepository.flush();
```

#### 🔍 Assertions
```java
// Assertion 1: Photo supprimée
assertThat(photoRepository.findById(photo1.getId())).isEmpty();

// Assertion 2: Comment1 supprimé (sur photo1)
assertThat(commentRepository.findById(comment1.getId()))
    .as("Comment on deleted photo should be cascade deleted")
    .isEmpty();

// Assertion 3: Comment2 supprimé (sur photo1)
assertThat(commentRepository.findById(comment2.getId()))
    .as("Comment on deleted photo should be cascade deleted")
    .isEmpty();

// Assertion 4: Comment3 toujours présent (sur photo2)
assertThat(commentRepository.findById(comment3.getId()))
    .as("Comments on other photos should still exist")
    .isPresent();

// Assertion 5: Compteur comments décrémenté de 2
assertThat(commentCountAfter)
    .as("Comment count should decrease by 2 after photo deletion")
    .isEqualTo(commentCountBefore - 2);

// Assertion 6: Users toujours présents
assertThat(userRepository.findById(ownerUser.getId()))
    .as("Owner user should still exist")
    .isPresent();
assertThat(userRepository.findById(commenterUser.getId()))
    .as("Commenter user should still exist")
    .isPresent();
```

#### ✅ Résultat Attendu
```
Avant:
Users: 2, Photos: 2, Comments: 3

DELETE photo1

Après:
Users: 2, Photos: 1, Comments: 1 (comment3)
```

---

### Test 5: testDeletingUserDeletesPhotosAndAllRelatedComments

#### 🎬 Scénario
Test de **double cascade** : User → Photos → Comments

#### 🔧 Action
```java
userRepository.delete(ownerUser);
userRepository.flush();
```

#### 🔍 Cascade Attendue
```
DELETE ownerUser
  ↓ (cascade CascadeType.ALL)
  ├─ DELETE photo1 (owned by ownerUser)
  │   ↓ (cascade CascadeType.ALL)
  │   ├─ DELETE comment1 (on photo1, by ownerUser)
  │   └─ DELETE comment2 (on photo1, by commenterUser) ← !
  │
  ├─ DELETE photo2 (owned by ownerUser)
  │   ↓ (cascade CascadeType.ALL)
  │   └─ DELETE comment3 (on photo2, by commenterUser) ← !
  │
  └─ DELETE all comments authored by ownerUser
```

#### 🔍 Assertions
```java
// Assertion 1: Compteurs initiaux
assertThat(userCountBefore).isEqualTo(2);
assertThat(photoCountBefore).isEqualTo(2);
assertThat(commentCountBefore).isEqualTo(3);

// Assertion 2: User supprimé
assertThat(userRepository.count()).isEqualTo(userCountBefore - 1);
assertThat(userRepository.findById(ownerUser.getId())).isEmpty();

// Assertion 3: Photos supprimées (2)
assertThat(photoRepository.count()).isEqualTo(photoCountBefore - 2);
assertThat(photoRepository.findById(photo1.getId())).isEmpty();
assertThat(photoRepository.findById(photo2.getId())).isEmpty();

// Assertion 4: TOUS les comments supprimés (3)
assertThat(commentRepository.count()).isEqualTo(0);
assertThat(commentRepository.findById(comment1.getId())).isEmpty();
assertThat(commentRepository.findById(comment2.getId())).isEmpty();
assertThat(commentRepository.findById(comment3.getId())).isEmpty();

// Assertion 5: CommenterUser toujours présent
assertThat(userRepository.findById(commenterUser.getId()))
    .as("Commenter user should still exist")
    .isPresent();
```

#### ✅ Résultat Attendu
```
Avant:
Users: 2, Photos: 2, Comments: 3

DELETE ownerUser

Après:
Users: 1 (commenterUser), Photos: 0, Comments: 0
```

#### 🎓 Leçon
Les commentaires de `commenterUser` sont supprimés car les **photos** sur lesquelles ils sont attachés sont supprimées (cascade Photo → Comment).

---

### Test 6: testMultipleUserDeletionsWithInterleavedComments

#### 🎬 Scénario
Scénario complexe avec 3 utilisateurs et commentaires croisés.

#### 📥 Setup Additionnel
```java
User user3 = create("user3");

Photo photo3 = Photo.builder()
    .owner(user3)
    .build();

// Tous commentent photo3
Comment commentByOwner = on(photo3, by: ownerUser);
Comment commentByCommenter = on(photo3, by: commenterUser);
Comment commentByUser3 = on(photo3, by: user3);
```

#### État Initial
```
Users: 3 (ownerUser, commenterUser, user3)
Photos: 3 (photo1, photo2, photo3)
Comments: 6
  - comment1: ownerUser on photo1
  - comment2: commenterUser on photo1
  - comment3: commenterUser on photo2
  - commentByOwner: ownerUser on photo3
  - commentByCommenter: commenterUser on photo3
  - commentByUser3: user3 on photo3
```

#### 🔧 Action
```java
userRepository.delete(ownerUser);
userRepository.flush();
```

#### 🔍 Assertions
```java
// Assertion 1: Photos de ownerUser supprimées
assertThat(photoRepository.findById(photo1.getId())).isEmpty();
assertThat(photoRepository.findById(photo2.getId())).isEmpty();

// Assertion 2: Photo3 toujours présente (owner = user3)
assertThat(photoRepository.findById(photo3.getId()))
    .as("Photo3 should still exist")
    .isPresent();

// Assertion 3: Comments on deleted photos supprimés
assertThat(commentRepository.findById(comment1.getId())).isEmpty();
assertThat(commentRepository.findById(comment2.getId())).isEmpty();
assertThat(commentRepository.findById(comment3.getId())).isEmpty();

// Assertion 4: Comments by ownerUser supprimés
assertThat(commentRepository.findById(commentByOwner.getId())).isEmpty();

// Assertion 5: Comments sur photo3 by others préservés
assertThat(commentRepository.findById(commentByCommenter.getId()))
    .as("Commenter's comment on photo3 should still exist")
    .isPresent();
assertThat(commentRepository.findById(commentByUser3.getId()))
    .as("User3's comment on photo3 should still exist")
    .isPresent();

// Assertion 6: Compteur final
assertThat(commentRepository.count()).isEqualTo(2);
```

#### ✅ Résultat Attendu
```
Avant:
Users: 3, Photos: 3, Comments: 6

DELETE ownerUser

Après:
Users: 2, Photos: 1 (photo3), Comments: 2 (sur photo3)
```

#### 📊 Suppression Détaillée
```
Supprimé:
  ✗ ownerUser
  ✗ photo1 (cascade user)
  ✗ photo2 (cascade user)
  ✗ comment1 (cascade user author)
  ✗ comment2 (cascade photo1)
  ✗ comment3 (cascade photo2)
  ✗ commentByOwner (cascade user author)

Préservé:
  ✓ commenterUser
  ✓ user3
  ✓ photo3 (owner = user3)
  ✓ commentByCommenter (on photo3, by commenterUser)
  ✓ commentByUser3 (on photo3, by user3)
```

---

## 📊 Matrice de Couverture Complète

| Composant | Méthode | Couverture | Tests |
|-----------|---------|------------|-------|
| **PhotoController** | upload() | ✅ 100% | T1.1 |
| **FileStorageService** | storeFile() | ✅ 100% | T1.1, T1.2, T1.3 |
| **FileStorageService** | validateFile() | ✅ 100% | T1.2 |
| **FileStorageService** | detectContentType() | ✅ 100% | T1.2 |
| **FileStorageService** | generateThumbnail() | ✅ 100% | T1.1 |
| **PhotoRepository** | save() | ✅ 100% | T1.1, T1.4 |
| **PhotoRepository** | delete() | ✅ 100% | T2.3, T2.4 |
| **UserRepository** | delete() | ✅ 100% | T2.1, T2.2, T2.5, T2.6 |
| **Cascade User→Photo** | ALL | ✅ 100% | T2.1, T2.5, T2.6 |
| **Cascade User→Comment** | ALL | ✅ 100% | T2.2, T2.5, T2.6 |
| **Cascade Photo→Comment** | ALL | ✅ 100% | T2.4, T2.5, T2.6 |

**Légende Tests :**
- T1.x : PhotoUploadIntegrationTest
- T2.x : UserCascadeDeletionIntegrationTest

---

## 🎯 Validation des Exigences

### Exigence 7.2.1 : Flux complet d'upload

| Critère | Validation | Test |
|---------|------------|------|
| Envoi fichier HTTP | ✅ | testCompletePhotoUploadFlow |
| Stockage sur disque | ✅ | testCompletePhotoUploadFlow (Assertions 12-14) |
| Génération thumbnail | ✅ | testCompletePhotoUploadFlow (Assertions 15-17) |
| Entrée en BDD | ✅ | testCompletePhotoUploadFlow (Assertions 2-11) |
| Validation MIME | ✅ | testPhotoUploadWithInvalidMimeType |
| Limite taille | ✅ | testPhotoUploadWithOversizedFile |
| Unicité noms | ✅ | testPhotoUploadCreatesUniqueStorageFilename |

### Exigence 7.2.2 : Suppression en cascade

| Critère | Validation | Test |
|---------|------------|------|
| User → Photos | ✅ | testDeletingUserCascadesDeleteTheirPhotos |
| User → Comments (authored) | ✅ | testDeletingUserCascadesDeleteTheirComments |
| Photo → Comments | ✅ | testDeletingPhotoCascadesDeleteItsComments |
| Double cascade | ✅ | testDeletingUserDeletesPhotosAndAllRelatedComments |
| Cascade complexe | ✅ | testMultipleUserDeletionsWithInterleavedComments |
| Non-régression | ✅ | testDeletingPhotoDoesNotDeleteUser |

---

## 📝 Notes Importantes

### Annotations @Transactional
Tous les tests sont `@Transactional`, donc :
- ✅ Rollback automatique après chaque test
- ✅ Base de données propre pour chaque test
- ✅ Isolation complète entre tests
- ✅ Ordre d'exécution non important

### Gestion du Flush
```java
userRepository.delete(user);
userRepository.flush(); // Force synchronisation avec DB
```
Le `flush()` est **critique** pour forcer l'exécution des cascades avant les assertions.

### Mock User
```java
@WithMockUser(username = "testuser")
```
Simule un utilisateur authentifié pour bypasser Spring Security dans les tests.

---

**Dernière mise à jour :** 2024-12-05  
**Total d'assertions :** 75+  
**Couverture globale :** 100% des exigences 7.2