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
package org.powerapi.module.rapl

import java.io.FileInputStream
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.DurationInt

class RAPLHelperSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("RAPLHelperSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  val basepath = getClass.getResource("/").getPath

  "The RAPLHelper" should "be able to read configuration parameters (with or without prefix parameter)" in {
    val helper = new RAPLHelper

    helper.cpuInfoPath should equal("p1")
    helper.msrPath should equal("p2")
  }

  "The method getRAPLEnergy in the RAPLHelper" should "return an estimation of CPU energy consumption" in {
    val helper = new RAPLHelper {
      override lazy val msrFile =  Some(new FileInputStream(s"${basepath}dev/cpu/0/msr").getChannel)
    }

    helper.energyUnits should equal(0.0000152587890625)
    helper.getRAPLEnergy should equal(9695.402465820312)
  }
}