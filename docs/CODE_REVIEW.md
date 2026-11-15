# Revues Codex pour TicketFlow

L'IA aide à examiner les PR et à préparer les corrections. Une personne habilitée conserve l'approbation finale et le merge.

## Activer les revues pour l'équipe

1. Ouvrir [les paramètres de revue de code](https://chatgpt.com/settings/code-review) avec le compte connecté à GitHub.
2. Rechercher puis ouvrir exactement Yassine-Atiki/ticketflow (et non ticketflow-config).
3. Activer la revue de code automatique au niveau du dépôt pour toutes les PR de l'équipe, si cette option est disponible. Le réglage du dépôt prime sur les préférences personnelles ; une préférence limitée à ses propres PR ne suffit pas pour les six membres.
4. Dans les options de déclenchement disponibles, choisir l'ouverture/la sortie du brouillon et les nouvelles modifications si proposées. Vérifier le libellé effectivement enregistré. Ne pas modifier les préférences des autres dépôts pour configurer TicketFlow.
5. Ne pas activer de consommation additionnelle de crédits ou une autre fonction payante pour cette installation.
6. Vérifier sur une vraie PR vers develop, puis sur une PR légitime vers main : compte bot, réaction ou revue visible et commit effectivement examiné. Pour les mises à jour non couvertes par le déclencheur choisi, redemander explicitement une revue.

Le fichier AGENTS.md guide la revue mais n'active pas le service. Aucun workflow GitHub Actions supplémentaire ni secret API n'est nécessaire pour la fonction native décrite ici.
Si le dépôt ou le réglage est absent, vérifier la connexion GitHub et les droits push/admin ; ne pas élargir les permissions ou changer d'abonnement automatiquement.

## Demander une revue ou une correction

Dans un commentaire de PR, pour demander une revue :

```text
@codex review
```

Après un nouveau commit, vérifier que la revue couvre le nouveau SHA. Redemander une revue au besoin. L'absence de remarque ne garantit pas l'absence de défaut.

Pour demander une correction identifiée, après accord sur le constat :

```text
@codex fix the P1 issue. Limit the change to the finding discussed in this PR and add a regression test. Follow AGENTS.md. Do not approve or merge this PR, change branch protections, alter the professor's CI workflow, or push to main/develop. Report the changes and test results.
```

Selon la connexion et les droits disponibles, Codex peut démarrer une tâche cloud et pousser un correctif sur la branche de la PR. Ce commentaire est donc une demande d'écriture, à utiliser volontairement.
Si cette assistance cloud est indisponible, ouvrir une tâche Codex avec l'URL de la PR et la même consigne, puis faire proposer le correctif sur une branche de contribution.

## Validation humaine

1. Le membre crée une PR de sa branche vers develop en décrivant le comportement et les tests.
2. Un humain examine les constats IA, le diff et les résultats CI ; il distingue les défauts introduits des échecs déjà présents.
3. L'auteur ou Codex, sur demande explicite, corrige sur la branche de contribution.
4. Après les derniers commits, l'humain revoit le résultat, résout les discussions réellement traitées et soumet Approve ou Request changes.
5. Une personne habilitée effectue le merge. La promotion develop vers main reste une décision humaine distincte.

Les instructions AGENTS.md interdisent à l'agent d'approuver ou de fusionner ; elles ne constituent pas une restriction technique de compte. Les règles GitHub contrôlent les approbations et les droits existants. Aucune nouvelle restriction ni automatisation de merge n'est installée ici.

## État observé avant cette configuration

- main et develop pointaient sur d4e64f51645f4ebf51d82d3f584bc9f4cb88f960.
- Deux rulesets actifs, Protect main et Protect develop, exigeaient chacun une PR, une approbation, l'invalidation des approbations après push, l'approbation du dernier push et la résolution des discussions. Aucun acteur de contournement n'était déclaré.
- Les deux rulesets empêchaient les suppressions et les force-push ; aucun required status check n'y figurait.
- L'auto-merge du dépôt était désactivé. Aucun CODEOWNERS ni AGENTS.md n'était présent.
- Les lectures des anciennes protections de branche ont retourné 403 avec le connecteur GitHub. Les rulesets ont pu être lus intégralement ; l'audit ne certifie pas l'absence d'une protection classique supplémentaire.
- Le workflow du professeur utilise Java 21 et ./mvnw -B -ntp verify. Le wrapper est exécutable et le POM parent déclare les quatre modules présents.
- [La CI de référence sur la PR de test](https://github.com/Yassine-Atiki/ticketflow/actions/runs/41538951466) a échoué : 7 tests, 4 erreurs de chargement du contexte de customer-service. Le client Config tente de joindre http://localhost:8888/customer-service/default/main et reçoit Connection refused. Les trois modules suivants sont SKIPPED.
- La configuration du client contient un import obligatoire du Config Server. Une correction de l'isolation des tests peut être proposée dans une PR séparée, sans désactiver les tests ni réécrire le workflow pour masquer l'échec.
- Les README et la feuille de route servent de contexte ; le cahier des charges complet et les contrats OpenAPI/Kafka/MCP ne sont pas versionnés dans cette base. Les règles ne fixent donc pas de rôles, schémas ou endpoints futurs arbitraires.

Ces constats décrivent l'état observé ; ils ne signifient pas que les revues automatiques ont été activées ou testées. La preuve est un réglage enregistré et une revue réelle sur le commit concerné.
Cette contribution ajoute uniquement AGENTS.md et ce guide ; les fichiers du professeur et les règles GitHub restent intacts.

## Références officielles

- [Configuration, déclenchement et corrections Codex sur GitHub](https://learn.chatgpt.com/docs/third-party/github)
- [Règles personnalisées dans AGENTS.md](https://developers.openai.com/blog/custom-code-review-rules-for-codex)
