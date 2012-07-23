import sbt._
import Keys._
import java.io.File

object Packaging {

  lazy val moreJars = TaskKey[Set[File]]("more-jars", "build NetLogoLite.jar and HubNet.jar")

  val settings = Seq(
    artifactName := { (_, _, _) => "NetLogo.jar" },
    packageOptions <+= dependencyClasspath in Runtime map {
      classpath =>
        Package.ManifestAttributes((
          "Class-Path", classpath.files
            .map(f => "lib/" + f.getName)
            .filter(_.endsWith(".jar"))
            .mkString(" ")))},
    packageBin in Compile <<= (packageBin in Compile, baseDirectory, cacheDirectory) map {
      (jar, base, cacheDir) =>
        val cache =
          FileFunction.cached(cacheDir / "NetLogo-jar", inStyle = FilesInfo.hash, outStyle = FilesInfo.hash) {
            in: Set[File] =>
              IO.copyFile(jar, base / "NetLogo.jar")
              Set(base / "NetLogo.jar")
          }
        cache(Set(jar))
        jar
      },
    moreJars <<= (packageBin in Compile, scalaInstance, baseDirectory, cacheDirectory, streams) map {
      (jar, instance, base, cacheDir, s) =>
        val cache =
          FileFunction.cached(cacheDir / "jars", inStyle = FilesInfo.hash, outStyle = FilesInfo.hash) {
            in: Set[File] =>
              IO.delete(base / "NetLogoLite.jar")
              IO.delete(base / "HubNet.jar")
              val scalaLibrary = instance.libraryJar.getAbsolutePath
              runProGuard(scalaLibrary, "lite", s.log)
              runProGuard(scalaLibrary, "hubnet", s.log)
              addManifest("HubNet", "manifesthubnet")
              Set(base / "NetLogoLite.jar",
                  base / "HubNet.jar")
          }
        cache(Set(jar))
      }
  )

  private def runProGuard(scalaLibraryPath: String, config: String, log: Logger) {
    log.info("building " + config + " jar")
    val javaLibraryPath = System.getProperty("java.home") +
      (if (System.getProperty("os.name").startsWith("Mac"))
         "/../Classes/classes.jar"
       else
         "/lib/rt.jar")
    def doIt() {
      System.setProperty("org.nlogo.java-library", javaLibraryPath)
      System.setProperty("org.nlogo.scala-library", scalaLibraryPath)
      proguard.ProGuard.main(Array("@project/proguard/" + config + ".txt"))
    }
    assert(TrapExit(doIt(), log) == 0)
  }

  private def addManifest(name: String, manifest: String) {
    ("jar umf project/proguard/" + manifest + ".txt " + name + ".jar").!
  }


}
