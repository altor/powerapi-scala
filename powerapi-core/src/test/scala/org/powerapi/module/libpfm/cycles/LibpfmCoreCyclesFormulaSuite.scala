/*
 * This software is licensed under the GNU Affero General Public License, quoted below.
 *
 * This file is a part of PowerAPI.
 *
 * Copyright (C) 2011-2016 Inria, University of Lille 1.
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
package org.powerapi.module.libpfm.cycles

import java.util.UUID

import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.{EventFilter, TestActorRef}
import akka.util.Timeout

import org.powerapi.UnitTest
import org.powerapi.core.power._
import org.powerapi.core.target.{Target, intToProcess}
import org.powerapi.core.{MessageBus, Tick}
import org.powerapi.module.FormulaChannel.{startFormula, stopFormula}
import org.powerapi.module.Formulas
import org.powerapi.module.PowerChannel.{RawPowerReport, subscribeRawPowerReport}
import org.powerapi.module.libpfm.PerformanceCounterChannel.{HWCounter, publishPCReport}

class LibpfmCoreCyclesFormulaSuite extends UnitTest {

  val timeout = Timeout(1.seconds)

  override def afterAll() = {
    system.terminate()
  }

  trait Bus {
    val eventBus = new MessageBus
  }

  trait Formulae {
    var formulae = Map[Double, List[Double]]()
    formulae += 12d -> List(85.7545270697, 1.10006565433e-08, -2.0341944068e-18)
    formulae += 13d -> List(87.0324917754, 9.03486530986e-09, -1.31575869787e-18)
    formulae += 14d -> List(86.3094440375, 1.04895773556e-08, -1.61982669617e-18)
    formulae += 15d -> List(88.2194900717, 8.71468661777e-09, -1.12354133527e-18)
    formulae += 16d -> List(85.8010062547, 1.05239105674e-08, -1.34813984791e-18)
    formulae += 17d -> List(85.5127064474, 1.05732955159e-08, -1.28040830962e-18)
    formulae += 18d -> List(85.5593567382, 1.07921513277e-08, -1.22419197787e-18)
    formulae += 19d -> List(87.2004521609, 9.99728883739e-09, -9.9514346029e-19)
    formulae += 20d -> List(87.7358230435, 1.00553994023e-08, -1.00002335486e-18)
    formulae += 21d -> List(94.4635683042, 4.83140424765e-09, 4.25218895447e-20)
    formulae += 22d -> List(104.356371072, 3.75414807806e-09, 6.73289818651e-20)
  }

  "A LibpfmCoreCyclesFormula" should "process a SensorReport and then publish a RawPowerReport" in new Bus with Formulae {
    val muid = UUID.randomUUID()
    val target: Target = 1

    val tick1 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis()
    }

    val tick2 = new Tick {
      val topic = "test"
      val timestamp = System.currentTimeMillis() + 1000
    }

    val formulas = TestActorRef(Props(classOf[Formulas], eventBus), "formulas")
    EventFilter.info(occurrences = 1, start = s"formula is started, class: ${classOf[LibpfmCoreCyclesFormula].getName}").intercept({
      startFormula(muid, target, classOf[LibpfmCoreCyclesFormula], Seq(eventBus, muid, target, "thread_p", "ref_p", formulae, 250.millis))(eventBus)
    })
    subscribeRawPowerReport(muid)(eventBus)(testActor)

    val values = Map[Int, Map[String, Seq[HWCounter]]](
      0 -> Map(
        "thread_p" -> Seq(HWCounter(650000000), HWCounter(651000000)),
        "ref_p" -> Seq(HWCounter(34475589), HWCounter(34075589))
      ),
      1 -> Map(
        "thread_p" -> Seq(HWCounter(0), HWCounter(0)),
        "ref_p" -> Seq(HWCounter(0), HWCounter(0))
      )
    )

    publishPCReport(muid, target, values, tick1)(eventBus)
    val rawPowerReport = expectMsgClass(classOf[RawPowerReport])
    rawPowerReport.muid should equal(muid)
    rawPowerReport.target should equal(target)
    rawPowerReport.power should be > 0.W
    rawPowerReport.device should equal("cpu")
    rawPowerReport.tick should equal(tick1)

    EventFilter.info(occurrences = 1, start = s"formula is stopped, class: ${classOf[LibpfmCoreCyclesFormula].getName}").intercept({
      stopFormula(muid)(eventBus)
    })

    publishPCReport(muid, target, values, tick2)(eventBus)
    expectNoMsg()

    Await.result(gracefulStop(formulas, timeout.duration), timeout.duration)
  }
}
