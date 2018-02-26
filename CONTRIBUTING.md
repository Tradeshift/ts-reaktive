Contributing to ts-reaktive
===========================

We welcome Github pull requests and issues created by anyone.

In order to contribute code, start by cloning/forking the project from git, and importing it into your IDE.

We require that pull requests:
- Fully describe the intent and use cases for the change
- Have informative commit messages according to [these rules](http://chris.beams.io/posts/git-commit/#seven-rules)  
    * Separate subject from body with a blank line
    * Limit the subject line to 50 characters
    * Capitalize the subject line
    * Do not end the subject line with a period
    * Use the imperative mood in the subject line
    * Wrap the body at 72 characters
    * Use the body to explain what and why vs. how
- Have unit tests for all non-trivial functionality, where a unit ideally does not span more than one class
- Have grammatically correct PR text

Release process
===============

We use <a href="https://semver.org/spec/v2.0.0.html">Semantic Versioning 2.0</a> to determine version numbers
for releases. Before making a release, decide whether it is a bump in the major, minor or bugfix version:

- MAJOR version when you make incompatible API changes
- MINOR version when you add functionality in a backwards-compatible manner
- BUGFIX version when you make backwards-compatible bug fixes

_NOTE: For versions before 1.0, we reserve the right to make API-incompatible changes in any version (but try
not to do so)._

The release process is as follows:

1. Decide which version number to bump (bugfix, minor or major).
2. Check out the master branch, and make sure you don't have any local changes.
3. Run the following sbt command (substituting `bugfix` as appropriate):
```
sbt -DBUMP=bugfix "release with-defaults"
```
4. Go to Github's [tags page](https://github.com/Tradeshift/ts-reaktive/tags), and fill out release notes
   for the new version. The release notes should be a bullet list of the first commit lines of "relevant"
   commits in the release. The format is flexible, feel free to summarize where needed. A quick way to get all
   the commits is to run `git log [TAG1]..[TAG2] --oneline`.
