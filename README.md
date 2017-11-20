# Snip Sample

A Scala system of [Actors](http://www.scala-lang.org/old/node/242) to evaluate the most popular news.

## Running this Code

Scala is a JVM language, and requires the [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) to run.

Once installed, Scala can be run through the `sbt` program. To install:

```bash
brew install sbt
```

(If you don't have Homebrew, you can install it [here](https://brew.sh)).

Running this project is as simple as:


```bash
sbt compile run
```

The sorted news items will be found in `./tmp/processed/`, in a timestamped csv file.

If you'd like to play around with any of the settings, such as the subreddits that the system travers, they can be found in `src/main/resources/application.conf`.