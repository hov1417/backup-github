import scala.language.postfixOps
import scala.sys.process.*

class Repository(name: String, sshUrl: String):

    def cloneTo(dir: String): Unit = {
        Seq(
            "git",
            "clone",
            this.sshUrl,
            dir + "/" + this.name
        ) !!<
    }

    def cleanClone(dir: String): Unit = {
        Seq(
            "rm",
            "-rf",
            dir + "/" + this.name
        ) !!<
    }

    def bundle(dir: String): Unit = {
        Seq(
            "git",
            "bundle",
            "create",
            dir + "/" + this.name + ".bundle",
            "--all"
        ) !!<
    }

def cleanDir(dir: String): Unit = Seq("rm", "-rf", dir) !!

private def homedir = System.getProperty("user.home");

def bundleDir(dir: String): Unit = Seq(
    "tar",
    "-cf",
    homedir + "/Backup/github/" + java.time.LocalDate.now.toString + "-bundles.tar",
    dir
) !!

def getRepositories: List[Repository] = {
    val repositories = ujson.read(
        Seq(
            "gh",
            "repo",
            "list",
            "--no-archived",
            "--json",
            "sshUrl",
            "--json",
            "name"
        ) !!<
    )
    repositories.arr
        .map(v => v.obj)
        .map(v => Repository(v.get("name").get.str, v.get("sshUrl").get.str))
        .toList
}
@main
def main(): Unit = {
    val repositories = getRepositories
    val tmpDir       = "/tmp/github"

    cleanDir(tmpDir)

    repositories.foreach(r => r.cloneTo(tmpDir))
    repositories.foreach(r => r.bundle(tmpDir))
    repositories.foreach(r => r.cleanClone(tmpDir))

    bundleDir(tmpDir)

    cleanDir(tmpDir)
}
