package su.assassin.mikrotik.rkn.list

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

  def removeOld(toRemove: Set[String]): Unit = toRemove.headOption.foreach { _ =>
    log.info(s"Start remove old addresses, ${toRemove.size}, may be very long...")
    toRemove
      .grouped(removeGroupSize)
      .foldLeft(0) { case (count, toRemove) =>
        val resultCount = count + toRemove.size
        log.debug(s"Remove $count-$resultCount...")
        val list = toRemove.map { ip =>
          s"""(list="$addressListName" && address="$ip")"""
        }.mkString(" || ")
        Seq("ssh", s"$user@$address", s"""ip firewall address-list remove [find where $list]""")
          .lineStream
          .foreach(value => log.debug("Remove output: " + value))
        resultCount
      }
    log.info("Dome removing")
  }

  def addNew(toAdd: Set[String]): Unit = toAdd.headOption.foreach { _ =>
    log.info(s"Start adding new, ${toAdd.size}")
    toAdd
      .toList
      .grouped(addGroupSize)
      .foldLeft(0) { case (count, toAdd) =>
        val resultCount = count + toAdd.size
        log.debug(s"Add $count-$resultCount...")
        val addressList = toAdd.map { ip =>
          s"""ip firewall address-list add address=$ip list=$addressListName"""
        }.mkString(";")
        Seq("ssh", s"$user@$address", addressList)
          .lineStream
          .foreach(value => log.debug("Add output: " + value))
        resultCount
      }
    log.info("Adding done")
  }

  def endSound: String = {
    log.info("Run end sound")
    s"ssh $user@$address $doneSound".!!
  }

  def generateRemoveList(oldList: Set[String], newList: Set[String]): Set[String] = {
    log.info("Generating 'remove-list'")
    oldList.diff(newList)
  }

  def generateAddList(oldList: Set[String], newList: Set[String]): Set[String] = {
    log.info("Generating 'add-list'")
    newList.diff(oldList)
  }

  def loadBlockedAddresses: Set[String] = {
    log.info(s"Start load blacklist from $blockedListUrl")
    val baseData = scala.io.Source.fromURL(blockedListUrl, "windows-1251")
      .getLines()
      .flatMap { line =>
        line.split(";")
          .headOption
      }.flatMap(_.split("\\|"))
      .collect {
        case subnetRegex(ip) => ip
        case ipRegex(ip) => ip
      }
      .toSet
    log.info(s"Done load blacklist, loaded ${baseData.size} banned addresses")

    val data = baseData
      .map(_.split("\\."))
      .map { list =>
        (list.take(3).mkString("."), list.last)
      }
      .toList
      .groupBy(_._1)

    val lowData = data
      .filter(_._2.size < AppConfig.countAddressToCompress)
      .flatMap { case (_, values) =>
        values.map { case (one, last) =>
          s"$one.$last"
        }
      }

    val highData = data
      .filter(_._2.size >= AppConfig.countAddressToCompress)
      .map { case (first, _) =>
        s"$first.0/24"
      }

    val result = highData ++ lowData
    log.info(s"After compression left ${result.size} banned addresses")
    result.toSet
  }

  def loadExistsAddresses: Set[String] = {
    log.info("Start ssh connect")
    val sshCommand =
      s"""ssh $user@$address ip firewall address-list print without-paging where list=$addressListName """.stripMargin
    log.debug(sshCommand)
    val addresses = sshCommand
      .lineStream
      .collect {
        case subnetRegex(ip) => ip
        case ipRegex(ip) => ip
      }
      .map { value =>
        log.debug("Loaded address: " + value)
        value
      }
      .toSet
    log.info(s"Done ssh connect, loaded ${addresses.size} addresses")
    addresses
  }

}
