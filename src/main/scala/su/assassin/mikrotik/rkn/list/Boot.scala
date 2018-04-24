package su.assassin.mikrotik.rkn.list

import java.time.LocalDateTime

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process._

object Boot {

  private val log = LoggerFactory.getLogger(this.getClass)

  import AppConfig._

  def main(args: Array[String]): Unit = {
    log.info("Start")
    val futureExistsAddresses = Future(loadExistsAddresses)
    val futureBlockedAddresses = Future(loadBlockedAddresses)

    val action = for {
      existsAddresses <- futureExistsAddresses
      blockedAddresses <- futureBlockedAddresses
      futureToRemove = Future(generateRemoveList(existsAddresses, blockedAddresses))
      futureToAdd = Future(generateAddList(existsAddresses, blockedAddresses))
      toRemove <- futureToRemove
      toAdd <- futureToAdd
    } yield (toRemove, toAdd)
    log.info("Await done operations, may be long...")
    val (toRemove, toAdd) = Await.result(action, Duration.Inf)
    removeOld(toRemove)
    addNew(toAdd)
    endSound
    log.info("Done!")
  }

  def removeOld(toRemove: Map[String, Unit]): Unit = toRemove.headOption.foreach { _ =>
    log.info(s"Start remove old addresses, ${toRemove.size}, may be very long...")
    toRemove
      .view
      .grouped(removeGroupSize)
      .foldLeft(0) { case (count, toRemove) =>
        val resultCount = count + toRemove.size
        log.debug(s"Remove $count-$resultCount...")
        val list = toRemove.map { case (ip, _) =>
          s"""(list="$addressListName" && address="$ip")"""
        }.mkString(" || ")
        val removeCommand = Seq("ssh", s"$user@$address", s"""ip firewall address-list remove [find where $list]""")
        removeCommand.!!
        resultCount
      }
    log.info("Dome removing")
  }

  def addNew(toAdd: Map[String, Unit]): Unit = toAdd.headOption.foreach { _ =>
    log.info(s"Start adding new, ${toAdd.size}")
    toAdd
      .grouped(addGroupSize)
      .foldLeft(0) { case (count, toAdd) =>
        val resultCount = count + toAdd.size
        log.debug(s"Add $count-$resultCount...")
        val addressList = toAdd.map { case (ip, _) =>
          s"""ip firewall address-list add address=$ip list=$addressListName"""
        }.mkString("\n")
        val addCommand = Seq("ssh", s"$user@$address", addressList)
        addCommand.!!
        count
      }
    log.info("Adding done")
  }

  def endSound: String = {
    log.info("Run end sound")
    s"ssh $user@$address $doneSound".!!
  }

  def generateRemoveList(oldList: Map[String, Unit], newList: Map[String, Unit]): Map[String, Unit] = {
    log.info("Generating 'remove-list'")
    oldList
      .filter { case (ip, _) =>
        newList.get(ip).isEmpty
      }
  }

  def generateAddList(oldList: Map[String, Unit], newList: Map[String, Unit]): Map[String, Unit] = {
    log.info("Generating 'add-list'")
    newList
      .filter { case (ip, _) =>
        oldList.get(ip).isEmpty
      }
  }

  def loadBlockedAddresses: Map[String, Unit] = {
    log.info(s"Start load blacklist from $blockedListUrl")
    val result = scala.io.Source.fromURL(blockedListUrl, "windows-1251")
      .getLines()
      .flatMap { line =>
        line.split(";")
          .headOption
      }.flatMap(_.split("\\|"))
      .collect {
        case subnetRegex(ip) => ip
        case ipRegex(ip) => ip
      }.map(_ -> ())
      .toMap
    log.info(s"Done load blacklist, loaded ${result.size} banned addresses")
    result
  }

  def loadExistsAddresses: Map[String, Unit] = {
    log.info("Start ssh connect")
    val sshCommand =
      s"""ssh $user@$address ip firewall address-list print without-paging where list=$addressListName """.stripMargin
    log.debug(sshCommand)
    val result: List[String] = sshCommand
      .!!
      .split("\n")
      .toList
    log.debug(s"Ssh connect done, readed ${result.length} rows")
    val addresses = result
      .view
      .collect {
        case subnetRegex(ip) => ip
        case ipRegex(ip) => ip
      }.map(_ -> ())
      .toMap
    log.info(s"Done ssh connect, loaded ${addresses.size} addresses")
    addresses
  }

}
