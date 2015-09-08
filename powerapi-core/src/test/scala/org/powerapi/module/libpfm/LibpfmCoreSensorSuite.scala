/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2015 Inria, University of Lille 1.
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
 *
 * If not, please consult http://www.gnu.org/licenses/agpl-3.0.html.
 */
package org.powerapi.module.libpfm

import akka.actor.{Terminated, ActorSystem, Props}
import akka.testkit.TestKit
import akka.util.Timeout
import akka.pattern.gracefulStop
import akka.testkit.{TestActorRef, TestProbe}
import java.util.UUID
import org.powerapi.UnitTest
import org.powerapi.core.MessageBus
import org.powerapi.core.target.{Process, All}
import org.powerapi.core.ClockChannel.ClockTick
import org.powerapi.core.MonitorChannel.MonitorTick
import org.powerapi.module.SensorChannel.{MonitorStop, MonitorStopAll}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{PCReport, subscribePCReport}
import org.scalamock.scalatest.MockFactory
import scala.collection.BitSet
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class LibpfmCoreSensorSuite(system: ActorSystem) extends UnitTest(system) with MockFactory {

  def this() = this(ActorSystem("LibpfmCoreSensorSuite"))

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  val topology = Map(0 -> Set(0, 1), 1 -> Set(2, 3))
  val events = Set("event", "event1")

  "A LibpfmCoreSensor" should "aggregate the performance counters per core/event" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val muid1 = UUID.randomUUID()

    val sensor = TestActorRef(Props(classOf[LibpfmCoreSensor], eventBus, helper, Timeout(1.seconds), topology, configuration, events), "core-sensor")(system)
    subscribePCReport(eventBus)(testActor)

    helper.resetPC _ expects * anyNumberOfTimes() returning true
    helper.enablePC _ expects * anyNumberOfTimes() returning true
    helper.disablePC _ expects * anyNumberOfTimes() returning true
    helper.closePC _ expects * anyNumberOfTimes() returning true

    helper.configurePC _ expects(-1, 0, configuration, "event", -1, 0l) returning Some(0)
    helper.configurePC _ expects(-1, 0, configuration, "event1", -1, 0l) returning Some(1)
    helper.configurePC _ expects(-1, 1, configuration, "event", -1, 0l) returning Some(2)
    helper.configurePC _ expects(-1, 1, configuration, "event1", -1, 0l) returning Some(3)
    helper.configurePC _ expects(-1, 2, configuration, "event", -1, 0l) returning Some(4)
    helper.configurePC _ expects(-1, 2, configuration, "event1", -1, 0l) returning Some(5)
    helper.configurePC _ expects(-1, 3, configuration, "event", -1, 0l) returning Some(6)
    helper.configurePC _ expects(-1, 3, configuration, "event1", -1, 0l) returning Some(7)
    helper.readPC _ expects * repeat 8 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, 1, ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(Process(1))
        wrappers.isEmpty should equal(true)
      }
    }
    sensor ! MonitorTick("monitor", muid1, All, ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(All)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for(wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(2l)
            }
          }
        }
      }
    }

    helper.readPC _ expects 0 returning Array(5, 2, 2)
    helper.readPC _ expects 1 returning Array(6, 2, 2)
    helper.readPC _ expects 2 returning Array(7, 2, 2)
    helper.readPC _ expects 3 returning Array(8, 2, 2)
    helper.readPC _ expects 4 returning Array(10, 2, 2)
    helper.readPC _ expects 5 returning Array(11, 2, 2)
    helper.readPC _ expects 6 returning Array(12, 2, 2)
    helper.readPC _ expects 7 returning Array(13, 2, 2)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(5l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(4)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(6l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(5)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(7l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(6)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(8l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(7)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(10l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(9)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(11l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(10)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(12l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(11)
    helper.scale _ expects where {
      (now: Array[Long], old: Array[Long]) => now.deep == Array(13l, 2l, 2l).deep && old.deep == Array(1l, 1l, 1l).deep
    } returning Some(12)
    val results = Map[(Int, String), Long]((0, "event") -> 10, (0, "event1") -> 12, (1, "event") -> 20, (1, "event1") -> 22)
    sensor ! MonitorTick("monitor", muid1, All, ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(All)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for((core, _) <- topology) {
          for(event <- events) {
            Future.sequence(wrappers.filter(wrapper => wrapper.core == core && wrapper.event == event).head.values) onSuccess {
              case values: List[Long] => values.foldLeft(0l)((acc, value) => acc + value) should equal(results(core, event))
            }
          }
        }
      }
    }

    Await.result(gracefulStop(sensor, timeout.duration), timeout.duration)
  }

  it should "close correctly the resources" in new Bus {
    val configuration = BitSet()
    val helper = mock[LibpfmHelper]
    val reaper = TestProbe()(system)
    val muid1 = UUID.randomUUID()
    val muid2 = UUID.randomUUID()

    val sensor = TestActorRef(Props(classOf[LibpfmCoreSensor], eventBus, helper, Timeout(1.seconds), topology, configuration, events), "core-sensor1")(system)
    subscribePCReport(eventBus)(testActor)

    helper.resetPC _ expects * anyNumberOfTimes() returning true
    helper.enablePC _ expects * anyNumberOfTimes() returning true
    helper.disablePC _ expects * anyNumberOfTimes() returning true
    helper.closePC _ expects * anyNumberOfTimes() returning true

    helper.configurePC _ expects(-1, 0, configuration, "event", -1, 0l) returning Some(0)
    helper.configurePC _ expects(-1, 0, configuration, "event1", -1, 0l) returning Some(1)
    helper.configurePC _ expects(-1, 1, configuration, "event", -1, 0l) returning Some(2)
    helper.configurePC _ expects(-1, 1, configuration, "event1", -1, 0l) returning Some(3)
    helper.configurePC _ expects(-1, 2, configuration, "event", -1, 0l) returning Some(4)
    helper.configurePC _ expects(-1, 2, configuration, "event1", -1, 0l) returning Some(5)
    helper.configurePC _ expects(-1, 3, configuration, "event", -1, 0l) returning Some(6)
    helper.configurePC _ expects(-1, 3, configuration, "event1", -1, 0l) returning Some(7)
    helper.readPC _ expects * repeat 8 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid1, All, ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(All)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(2l)
            }
          }
        }
      }
    }
    helper.configurePC _ expects(-1, 0, configuration, "event", -1, 0l) returning Some(8)
    helper.configurePC _ expects(-1, 0, configuration, "event1", -1, 0l) returning Some(9)
    helper.configurePC _ expects(-1, 1, configuration, "event", -1, 0l) returning Some(10)
    helper.configurePC _ expects(-1, 1, configuration, "event1", -1, 0l) returning Some(11)
    helper.configurePC _ expects(-1, 2, configuration, "event", -1, 0l) returning Some(12)
    helper.configurePC _ expects(-1, 2, configuration, "event1", -1, 0l) returning Some(13)
    helper.configurePC _ expects(-1, 3, configuration, "event", -1, 0l) returning Some(14)
    helper.configurePC _ expects(-1, 3, configuration, "event1", -1, 0l) returning Some(15)
    helper.readPC _ expects * repeat 8 returning Array(1, 1, 1)
    sensor ! MonitorTick("monitor", muid2, All, ClockTick("clock", 1.second))
    expectMsgClass(classOf[PCReport]) match {
      case PCReport(_, _, target, wrappers, _) => {
        target should equal(All)
        wrappers.size should equal(topology.size * events.size)
        events.foreach(event => wrappers.count(_.event == event) should equal(topology.size))
        wrappers.foreach(wrapper => wrapper.values.size should equal(topology(0).size))

        for (wrapper <- wrappers) {
          Future.sequence(wrapper.values) onSuccess {
            case coreValues: List[Long] => {
              val aggValue = coreValues.foldLeft(0l)((acc, value) => acc + value)
              aggValue should equal(2l)
            }
          }
        }
      }
    }
    var children = sensor.children.toArray.clone().filter(_.path.name.contains(muid1.toString))
    children.foreach(child => reaper watch child)
    children.size should equal(8)
    sensor ! MonitorStop("sensor", muid1)
    for(_ <- 0 until children.size) {
      reaper.expectMsgClass(classOf[Terminated])
    }

    children = sensor.children.toArray.clone()
    children.foreach(child => reaper watch child)
    children.size should equal(8)
    sensor ! MonitorStopAll("sensor")
    for(_ <- 0 until children.size) {
      reaper.expectMsgClass(classOf[Terminated])
    }

    Await.result(gracefulStop(sensor, timeout.duration), timeout.duration)
  }
}
