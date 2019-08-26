import Versions._

lazy val commonSettings = Seq(
  name := "dbpedia-spotlight-model",
  organization := "io.grhodes",
  version := appVersion,
  scalaVersion := "2.12.9",

  fork in run := true,
  fork in test := true,

  resolvers += Resolver.bintrayRepo("grahamar", "maven"),
  resolvers += "spring" at "https://repo.spring.io/libs-milestone/",
  resolvers += "opennlp.sf.net" at "http://opennlp.sourceforge.net/maven2/",
  resolvers += "anonsvn" at "http://anonsvn.icefaces.org/repo/maven2/releases/",
  resolvers += "sonatype" at "http://repository.sonatype.org/content/groups/public/",
  resolvers += "Java.net Repository for Maven" at "http://download.java.net/maven/2/",
  resolvers += "igetdb.sourceforge" at "http://igetdb.sourceforge.net/maven2-repository",
  resolvers += "Scala-tools Maven2 Repository" at "http://scala-tools.org/repo-releases/",
  resolvers += "Maven Java Net Snapshots and Releases" at "https://maven.java.net/content/groups/public/",
  resolvers += "University Leipzig, AKSW Maven2 Repository" at "http://maven.aksw.org/archiva/repository/internal",
  resolvers += "spotlight-releases-repository" at "https://github.com/dbpedia-spotlight/maven-repo/raw/master/releases",
  resolvers += "spotlight-snapshots-repository" at "https://github.com/dbpedia-spotlight/maven-repo/raw/master/snapshots",

  // Compile/Runtime dependencies
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.28"
  ),

  // Test dependencies
  libraryDependencies ++= Seq(
    "junit" % "junit" % "4.8.2" % Test,
    "org.scalatest" %% "scalatest" % "3.0.0" % Test,
    "org.mockito" % "mockito-core" % "2.7.22" % Test
  ),

  licenses += ("Apache-2.0", url("http://opensource.org/licenses/apache-2.0"))
)

lazy val root = (project in file("."))
  .aggregate(core, index, rest)
  .settings(commonSettings: _*)
  .settings(
    skip in publish := true
  )

lazy val core = project
  .settings(commonSettings: _*)
  .settings(
    name := "dbpedia-spotlight-model-core",
    libraryDependencies ++= Seq(
      "org.dbpedia.extraction" % "core" % "3.9",
      "jcs" % "jcs" % "1.3" exclude("xerces", "xerces"),
      "xerces" % "xercesImpl" % "2.9.1",
      "org.apache.solr" % "solr-analysis-extras" % "6.5.0",
      "commons-lang" % "commons-lang" % "2.5",
      "com.google.guava" % "guava" % "r07",
      "nekohtml" % "nekohtml" % "0.9.5",
      "org.apache.httpcomponents" % "httpclient" % "4.2",
      "org.semanticweb.yars" % "nxparser" % "1.1",
      "edu.umd" % "cloud9" % "SNAPSHOT",
      "weka" % "kea-goss-weka" % "5.0-SNAPSHOT",
      "weka" % "weka" % "3.7.3",
      "net.liftweb" %% "lift-json" % "3.0.1",
      "net.sf.opencsv" % "opencsv" % "2.0",
      "hsqldb" % "hsqldb" % "1.8.0.1",
      "com.mongodb" % "mongo" % "1.0",
      "it.unimi.dsi" % "fastutil" % "6.3",
      "opennlp" % "maxent" % "3.0.0",
      "org.apache.opennlp" % "opennlp-tools" % "1.5.3",
      "opennlp" % "tools" % "1.5.0",
      "org.json" % "json" % "20090211",
      "com.esotericsoftware.kryo" % "kryo" % "2.20",
      "org.apache.jdbm" % "jdbm" % "3.0-alpha5",
      "trove" % "trove" % "1.1-beta-5",
      "org.apache.mahout" % "mahout-collections" % "1.0",
      "org.apache.commons" % "commons-math" % "2.2",
      "io.grhodes" %% "scala-aho-corasick" % "0.3",
      "org.scalaz" %% "scalaz-core" % "7.1.17",
      "org.scalanlp" %% "breeze" % "0.13",
      "com.typesafe.akka" %% "akka-actor" % "2.4.12",
      "org.projectlombok" % "lombok" % "1.16.16" % Provided
    )
  )

lazy val index = project
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "dbpedia-spotlight-model-index",
    libraryDependencies ++= Seq(
      "org.dbpedia.extraction" % "core" % "3.9",
      "org.openrdf" % "rio" % "1.0.10",
      "org.apache.commons" % "commons-compress" % "1.4.1",
      "commons-io" % "commons-io" % "2.4",
      "weka" % "weka" % "3.7.3",
      "trove" % "trove" % "1.1-beta-5",
      "org.apache.jdbm" % "jdbm" % "3.0-alpha5",
      "org.apache.mahout" % "mahout-collections" % "1.0",
      "com.esotericsoftware.kryo" % "kryo" % "2.20",
      "org.codehaus.jackson" % "jackson-core-asl" % "1.9.8"
    )
  )

lazy val rest = project
  .dependsOn(core)
  .settings(commonSettings: _*)
  .settings(
    name := "dbpedia-spotlight-model-rest",
    libraryDependencies ++= Seq(
      "com.sun.jersey" % "jersey-server" % "1.19.3",
      "com.sun.jersey" % "jersey-client" % "1.19.3",
      "com.sun.jersey" % "jersey-grizzly" % "1.19.3",
      "com.sun.jersey" % "jersey-bundle" % "1.19.3",
      "com.sun.grizzly" % "grizzly-servlet-webserver" % "1.9.48",
      "xom" % "xom" % "1.2.5",
      "xerces" % "xmlParserAPIs" % "2.6.2",
      "xml-apis" % "xml-apis" % "1.0.b2",
      "net.sf.json-lib" % "json-lib" % "2.4" classifier "jdk15",
      "com.thoughtworks.xstream" % "xstream" % "1.3.1",
      "org.codehaus.jettison" % "jettison" % "1.3",
      "stax" % "stax-api" % "1.0.1",
      "de.l3s.boilerpipe" % "boilerpipe" % "1.1.0",
      "org.nlp2rdf" % "nif" % "0.4",
      "org.projectlombok" % "lombok" % "1.16.16" % Provided,
      "com.google.code.gson" % "gson" % "2.8.1"
    )
  )
