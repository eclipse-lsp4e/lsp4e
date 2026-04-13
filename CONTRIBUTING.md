# Contributing to Eclipse LSP4E

Welcome to the Eclipse LSP4E contributor land, and thanks in advance for your help in making Eclipse LSP4E better and better!

🏠 Official Eclipse LSP4E Git repo is [https://github.com/eclipse-lsp4e/lsp4e](https://github.com/eclipse-lsp4e/lsp4e) . (All other repositories, mirrors and so on are legacy repositories that should be removed at some point, so please don't use them!)

## ⚖️ Legal and Eclipse Foundation terms

The project license is available at [LICENSE](LICENSE).

This Eclipse Foundation open project is governed by the Eclipse Foundation
Development Process and operates under the terms of the Eclipse IP Policy.

Before your contribution can be accepted by the project team,
contributors must have an Eclipse Foundation account and
must electronically sign the Eclipse Contributor Agreement (ECA).

* [http://www.eclipse.org/legal/ECA.php](http://www.eclipse.org/legal/ECA.php)

For more information, please see the Eclipse Committer Handbook:
[https://www.eclipse.org/projects/handbook/#resources-commit](https://www.eclipse.org/projects/handbook/#resources-commit).


## 💬 Get in touch with the community

Eclipse LSP4E use mainly 2 channels for strategical and technical discussions

* 🐞 View and report issues through uses GitHub Issues at https://github.com/eclipse-lsp4e/lsp4e/issues.
* 📧 Join the lsp4e-dev@eclipse.org mailing-list to get in touch with other contributors about project organization and planning, and browse archive at 📜 [https://accounts.eclipse.org/mailing-list/lsp4e-dev](https://accounts.eclipse.org/mailing-list/lsp4e-dev)


## 🆕 Trying latest builds

Latest snapshot builds, for testing, can usually be found at `https://download.eclipse.org/lsp4e/snapshots/` .


## 🧑‍💻 Developer resources

### ⌨️ Setting up the Development Environment

#### Install Software

1. Install the **Eclipse IDE for Eclipse Committers** from https://www.eclipse.org/downloads/packages/ or
  any another Eclipse distribution with:
    1. [Plug-in Development Environment (PDE)](https://www.eclipse.org/pde/) installed
    1. Eclipse [m2e](https://www.eclipse.org/m2e/) installed
1. To run Maven builds from the command line, install [JDK17](https://adoptium.net/temurin/releases/?version=17) and [Maven 3.9.x](https://maven.apache.org/download.cgi)

#### Import Project

1. Clone this repository <a href="https://mickaelistria.github.io/redirctToEclipseIDECloneCommand/redirect.html"><img src="https://mickaelistria.github.io/redirctToEclipseIDECloneCommand/cloneToEclipseBadge.png" alt="Clone to Eclipse IDE"/></a> for LSP4E.
1. _File > Open Projects from Filesystem..._ , select the path to the LSP4E Git repo and the relevant children projects you want to import
1. Depending on the task you're planning to work on, multiple workflows are available to configure the [target-platform](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm?cp=4_1_5)
    * In many cases, this simplest workflow will be sufficient: Install latest LSP4E snapshot in your target-platform (can be your current IDE), or
    * If you don't want to mix versions of LSP4E, open [target-platforms/latest.target](target-platforms/latest.target) from within Eclipse and click **Set as Active Target-Platform** in the top right corner.
1. Open the project modules you want to work on (right-click > Open project) and their dependencies
1. Happy coding! 🤗

### 🏗️ Build

- From the command line run:
  - on Windows `mvnw clean verify`
  - on Linux/MacOS `./mvnw clean verify`
- From within Eclipse: right-click on the LSP4E root folder > Run As > Maven build

### Running single unit tests via the command line

To run single unit tests via the command line, one can use the following command from the project's root:
```
./mvnw -Dtest=<TestClassName>#<MethodName> -DfailIfNoTests=false verify
```

For example:
```
./mvnw -Dtest=HighlightTest#testCheckIfOtherAnnotationsRemains -DfailIfNoTests=false verify
```

To run a unit test in JVM debug mode via the command line one can use:
```
./mvnw -Dtest=<TestClassName>#<MethodName> -DfailIfNoTests=false -Dtycho.testArgLine="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=localhost:8000" verify
```
Once Maven is about to execute the test it will wait for you to attach to the test JVM using a remote debugger, e.g. using Eclipse's `Remote Java Application` debug configuration.


#### Running the CI job locally:

The GitHub actions wokflow is compatible with [nektos/act](https://github.com/nektos/act) a command-line tool that allows you to run GitHub action workflows locally.

1. Install the [docker engine](https://docs.docker.com/engine/install/)
1. Install [nektos/act](https://github.com/nektos/act)
1. From the command line navigate into the LSP4E project root
1. Run the command `act`
1. On subsequent re-runs you can use `act -r` to reuse previous container which avoids reinstallation system packages and reduces build time.

In case of build failures the docker container will still be running and you can ssh into it for analysis using `docker exec -u root -it <CONTAINER_ID> /bin/bash`, e.g.:
```bash
container_id=$(docker container ps --filter status=running --filter name=act-Build-build --format {{.ID}})
docker exec -u root -it $container_id /bin/bash
```

### ⬆️ Version bump

LSP4E tries to use OSGi Semantic Version (to properly expose its API contracts and breakage) and Reproducible Version Qualifiers (to minimize the avoid producing multiple equivalent artifacts for identical source).
This requires the developer to manually bump version from time to time. Some rules are that:

* Versions are bumped on a __per module grain__ (bump version of individual bundles/features one by one when necessary), __DON'T bump version of parent pom, nor of other modules you don't change__
* __Versions are bumped maximum once per release__ (don't bump versions that were already bumped since last release)
* __Don't bump versions of what you don't change__
* __Bump version of the bundles you're modifying only if it's their 1st change since last release__
* Version bump may need to be cascaded to features that *include* the artifact you just changed, and then to features that *include* such features and so on (unless the version of those features were already bumped since last release).

The delta for version bumps are:

* `+0.0.1` (next micro) for a bugfix, or an internal change that doesn't surface to APIs
* `+0.1.0` (next minor) for an API addition
* `+1.0.0` (next major) for an API breakage (needs to be discussed on the mailing-list first)
* If some "smaller" bump already took place, you can replace it with your "bigger one". Eg, if last release has org.eclipse.lsp4e 1.16.1; and someone already bumped version to 1.16.2 (for an internal change) and you're adding a new API, then you need to change version to 1.17.0

### ➕ Submit changes

LSP4E only accepts contributions via GitHub Pull Requests against [https://github.com/eclipse-lsp4e/lsp4e](https://github.com/eclipse-lsp4e/lsp4e) repository.

Before sending us a pull request, please ensure that:

1. You are working against the latest source on the **main** branch.
1. You check existing open and recently merged pull requests to make sure someone else hasn't already addressed the issue.

To send us a pull request, please:

1. Fork the repository.
1. Modify the source while focusing on the specific change you are contributing.
1. Commit to your fork using clear, descriptive commit messages.
1. Send us a pull request, answering any default questions in the pull request interface.

GitHub provides additional documentation on [forking a repository](https://help.github.com/articles/fork-a-repo/) and [creating a pull request](https://help.github.com/articles/creating-a-pull-request/)
