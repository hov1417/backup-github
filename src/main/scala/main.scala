import cats.effect.{ExitCode, IO, IOApp}
import github4s.GithubClient
import org.ekrich.config.{Config, ConfigException, ConfigFactory}
import org.http4s.client.JavaNetClientBuilder

import scala.io.Source
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
            "-C",
            dir + "/" + this.name,
            "bundle",
            "create",
            dir + "/" + this.name + ".bundle",
            "--all"
        ) !!<
    }

def cleanDir(dir: String): Unit = Seq("rm", "-rf", dir) !!

private val homedir       = System.getProperty("user.home");
private val configPath    = homedir + "/.config";
private val livConfigPath = configPath + "/liv/liv.conf"

def bundleDir(dir: String): Unit = Seq(
    "tar",
    "-cf",
    homedir + "/Backup/github/" + java.time.LocalDate.now.toString + "-bundles. tar",
    dir
) !!

extension (c: Config)
    def getReqStr(path: String): String =
        try c.getString(path)
        catch
            case e: ConfigException.Missing =>
                throw new Exception(path + " is missing in " + livConfigPath)
            case e: ConfigException.WrongType =>
                throw new Exception(
                    path + " is not a string in " + livConfigPath
                )

def getRepositories: IO[List[Repository]] = {
    for {
        configSource <- IO(Source.fromFile(livConfigPath))
            .bracket { source =>
                IO(source.mkString)
            } { source =>
                IO(source.close())
            }
            .recoverWith { e =>
                if (e.isInstanceOf[java.io.FileNotFoundException])
                    IO("")
                else
                    IO.raiseError(e)
            }
        rawRepositoriesListResult <- {
            val config = ConfigFactory.parseString(configSource)
            val token  = config.getReqStr("github.token")
            val user   = config.getReqStr("github.user")
            val gh =
                GithubClient[IO](JavaNetClientBuilder[IO].create, Some(token))
            gh.repos.listUserRepos(user, Some("all"))
        }
        rawRepositoriesList <- IO.fromEither(rawRepositoriesListResult.result)
        repositories = rawRepositoriesList.map(v =>
            Repository(v.name, v.urls.ssh_url)
        )
    } yield repositories
}

object Liv extends IOApp {
    def run(args: List[String]): IO[ExitCode] = {
        val tmpDir = "/tmp/github"

        cleanDir(tmpDir)

        for {
            repositories <- getRepositories
        } yield {
            repositories.foreach(r => r.cloneTo(tmpDir))
            repositories.foreach(r => r.bundle(tmpDir))
            repositories.foreach(r => r.cleanClone(tmpDir))

            bundleDir(tmpDir)

            cleanDir(tmpDir)
            ExitCode.Success
        }
    }
}
