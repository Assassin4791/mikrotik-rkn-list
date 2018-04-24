package su.assassin.mikrotik.rkn.list

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

import scala.util.matching.Regex

object AppConfig {

  private val path: String = scala.util.Properties.envOrNone("CONFIG_PATH")
    .getOrElse(throw new RuntimeException("Not set env CONFIG_PATH"))

  private val conf: Config = ConfigFactory.parseFile(new File(path))

  val ipRegex: Regex = conf.getString("ipRegex").r
  val subnetRegex: Regex = conf.getString("subnetRegex").r
  val addressListName: String = conf.getString("addressListName")
  val user: String = conf.getString("user")
  val address: String = conf.getString("address")
  val blockedListUrl: String = conf.getString("blockedListUrl")
  val removeGroupSize: Int = conf.getInt("removeGroupSize")
  val addGroupSize: Int = conf.getInt("addGroupSize")
  val doneSound: String = conf.getString("doneSound")
}
