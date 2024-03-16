import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import github4s.GithubClient
import org.eclipse.jgit
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.transport.BundleWriter
import org.eclipse.jgit.util.FileUtils
import org.ekrich.config.{Config, ConfigException, ConfigFactory}
import org.http4s.client.JavaNetClientBuilder

import java.io.{File, FileOutputStream}
import scala.io.Source
import scala.language.postfixOps
import scala.sys.process.*

class Repository(name: String, sshUrl: String):
    private var git: Git = _

    def cloneTo(dir: String): IO[Unit] = {
        IO.blocking {
            println("Cloning " + name + " to " + dir + "/" + name)
            this.git = Git
                .cloneRepository()
                .setURI(sshUrl)
                .setDirectory(new File(dir + "/" + name))
                .call()
            println("Cloned " + name)
        }
    }

    def cleanClone(dir: String): IO[Unit] = {
        IO.blocking {
            println("Cleaning " + name + " from " + dir)
            FileUtils.delete(new File(dir + "/" + name), FileUtils.RECURSIVE)
            println("Cleaned " + name)
        }
    }

    def bundle(dir: String): IO[Unit] = {
        IO.blocking {
            val repo = git.getRepository
            println("Bundling " + name)

            val bundle = new BundleWriter(repo)

            repo.getRefDatabase.getRefs
                .forEach(ref => bundle.include(ref))

            bundle.writeBundle(
                NullProgressMonitor.INSTANCE,
                new FileOutputStream(dir + "/" + name + ".bundle")
            )

            println("Bundled " + name)
        }
    }

def cleanDir(dir: String): Unit = Seq("rm", "-rf", dir) !!

private val homedir       = System.getProperty("user.home");
private val configPath    = homedir + "/.config";
private val livConfigPath = configPath + "/liv/liv.conf"

def bundleDir(dir: String): Unit = Seq(
    "tar",
    "-cf",
    homedir + "/Backup/github/" + java.time.LocalDate.now.toString + "-bundles.tar",
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
            Repository(v.name, v.urls.clone_url)
        )
    } yield repositories
}

object Liv extends IOApp {
    def run(args: List[String]): IO[ExitCode] = {
        val tmpDir = "/tmp/github"

        cleanDir(tmpDir)

        for {
            repositories <- getRepositories
            results <- repositories.parTraverse(r => {
                for {
                    _ <- r.cloneTo(tmpDir)
                    _ <- r.bundle(tmpDir)
                    _ <- r.cleanClone(tmpDir)
                } yield ()
            })
        } yield {
            bundleDir(tmpDir)

            cleanDir(tmpDir)
            ExitCode.Success
        }
    }
}
