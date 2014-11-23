/**
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2014 Inria, University of Lille 1.
 *
 * PowerAPI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * PowerAPI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with PowerAPI.

 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */

package org.powerapi.sensors.procfs.cpu.simple

import java.util.UUID

import org.powerapi.core._
import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.CacheKey

/**
 * CPU sensor configuration.
 *
 * @author abourdon
 * @author mcolmant
 */
trait Configuration extends org.powerapi.core.Configuration {
  import org.powerapi.core.ConfigValue

  /**
   * Global stat file, giving global information of the system itself.
   * Typically presents under /proc/stat.
   */
  lazy val globalStatPath = load { _.getString("powerapi.procfs.global-path") } match {
    case ConfigValue(path) => path
    case _ => "/proc/stat"
  }

  /**
   * Process stat file, giving information about the process itself.
   * Typically presents under /proc/[pid]/stat.
   */
  lazy val processStatPath = load { _.getString("powerapi.procfs.process-path") } match {
    case ConfigValue(path) if path.contains("%?pid") => path
    case _ => "/proc/%?pid/stat"
  }
}

/**
 * CPU sensor component that collects data from a /proc and /sys directories
 * which are typically presents under a Linux platform.
 *
 * @see http://www.kernel.org/doc/man-pages/online/pages/man5/proc.5.html
 *
 * @author abourdon
 * @author mcolmant
 */
class CpuSensor(eventBus: MessageBus, osHelper: OSHelper) extends Sensor(eventBus) with Configuration {
  import java.io.IOException

  import org.powerapi.core.MonitorChannel.MonitorTicks
  import org.powerapi.sensors.procfs.cpu.CpuSensorChannel.publishCpuReport
  import scala.io.Source

  /**
   * Delegate class collecting time information contained into both globalStatPath and processStatPath files
   * and providing the target CPU percent usage.
   */
  class TargetRatio {
    import org.powerapi.core.{Application, Process}
    import org.powerapi.sensors.procfs.cpu.FileControl.using

    private val GlobalStatFormat = """cpu\s+([\d\s]+)""".r

    /**
     * Internal cache, used to get the diff between two ClockTick.
     */
    lazy val cache = collection.mutable.Map[CacheKey, (Long, Long)]()

    def globalElapsedTime(): Option[Long] = {
      try {
        using(Source.fromFile(globalStatPath))(source => {
          log.debug("using {} as a procfs global stat path", globalStatPath)

          val time = source.getLines.toIndexedSeq(0) match {
            /**
             * Exclude all guest columns, they are already added in utime column.
             *
             * @see http://lxr.free-electrons.com/source/kernel/sched/cputime.c#L165
             */
            case GlobalStatFormat(times) => times.split("\\s").slice(0, 8).foldLeft(0: Long) {
              (acc, x) => acc + x.toLong
            }
          }

          Some(time)
        })
      }
      catch {
        case ioe: IOException => log.warning("i/o exception: {}", ioe.getMessage); None
      }
    }

    def processElapsedTime(process: Process): Option[Long] = {
      try {
        using(Source.fromFile(processStatPath.replace("%?pid", s"${process.pid}")))(source => {
          log.debug("using {} as a procfs process stat path", processStatPath)

          val statLine = source.getLines.toIndexedSeq(0).split("\\s")
          // User time + System time
          Some(statLine(13).toLong + statLine(14).toLong)
        })
      }
      catch {
        case ioe: IOException => log.warning("i/o exception: {}", ioe.getMessage); None
      }
    }

    def refreshCache(key: CacheKey, now: (Long, Long)): Unit = {
      cache += (key -> now)
    }

    def handleProcessTarget(process: Process): (Long, Long) = {
      lazy val processTime: Long = processElapsedTime(process) match {
        case Some(value) => value
        case _ => 0l
      }
      lazy val globalTime: Long = globalElapsedTime() match {
        case Some(value) => value
        case _ => 0l
      }

      (processTime, globalTime)
    }

    def handleApplicationTarget(application: Application): (Long, Long) = {
      lazy val processTime: Long = osHelper.getProcesses(application).foldLeft(0: Long) {
        (acc, process: Process) => {
          processElapsedTime(process) match {
            case Some(value) => acc + value
            case _ => acc
          }
        }
      }
      lazy val globalTime: Long = globalElapsedTime() match {
        case Some(value) => value
        case _ => 0l
      }
      (processTime, globalTime)
    }

    def handleTarget(muid: UUID, target: Target): org.powerapi.sensors.procfs.cpu.CpuSensorChannel.TargetRatio = {
      val now = target match {
        case process: Process => handleProcessTarget(process)
        case application: Application => handleApplicationTarget(application)
        case All => log.warning("target All is not handled by this sensor"); (0l, 0l)
      }

      val key = CacheKey(muid, target)
      val old = cache.getOrElse(key, now)
      refreshCache(key, now)
      val globalDiff = now._2 - old._2

      if (globalDiff <= 0) {
        org.powerapi.sensors.procfs.cpu.CpuSensorChannel.TargetRatio(0)
      }
      else {
        org.powerapi.sensors.procfs.cpu.CpuSensorChannel.TargetRatio((now._1 - old._1).doubleValue / globalDiff)
      }
    }
  }

  lazy val targetRatio = new TargetRatio

  def sense(monitorTargets: MonitorTicks): Unit = {
    for(target <- monitorTargets.subscription.targets) {
      publishCpuReport(monitorTargets.subscription.muid, target, targetRatio.handleTarget(monitorTargets.subscription.muid, target), monitorTargets.timestamp)(eventBus)
    }
  }
}
