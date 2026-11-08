# Copycat Architect Graphstudio 0.1

## Lokal starten

Im entpackten Ordner einen statischen Server starten.

```sh
python3 -m http.server 8000 --bind 127.0.0.1 --directory graphstudio
```

- [Gebäudegraph](http://localhost:8000/?editor=building)
- [Formgraph](http://localhost:8000/?editor=form)

## Repository und Wiki veröffentlichen

Die Patches sind eigenständige Git-Commits. Der Repository-Patch basiert auf
`138e745`, der Wiki-Patch auf `2e2aaf8`.

Aus dem entpackten Ordner mit GitHub-Schreibzugriff ausführen.

```sh
git clone https://github.com/TeutonStudio/copycat-architect.git
git -C copycat-architect switch -c web-graph-editor
git -C copycat-architect am "$PWD/0001-graphstudio-repository.patch"
git -C copycat-architect push -u origin web-graph-editor
```

In den [Pages-Einstellungen](https://github.com/TeutonStudio/copycat-architect/settings/pages)
unter **Source** die Option **GitHub Actions** wählen. Den Branch in `master`
übernehmen. Den Workflow **Graphstudio** ausführen, falls er beim Merge nicht
automatisch startet. Die Website sollte danach unter
[teutonstudio.github.io/copycat-architect](https://teutonstudio.github.io/copycat-architect/)
erreichbar sein.

Sobald die Website erreichbar ist, das Wiki aktualisieren.

```sh
git clone https://github.com/TeutonStudio/copycat-architect.wiki.git copycat-architect-wiki
git -C copycat-architect-wiki am "$PWD/0002-graphstudio-wiki.patch"
git -C copycat-architect-wiki push origin master
```
