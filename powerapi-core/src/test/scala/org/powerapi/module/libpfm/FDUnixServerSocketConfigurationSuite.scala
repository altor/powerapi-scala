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

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.util.Timeout
import org.powerapi.UnitTest
import scala.concurrent.duration.DurationInt

class FDUnixServerSocketConfigurationSuite(system: ActorSystem) extends UnitTest(system) {

  implicit val timeout = Timeout(1.seconds)

  def this() = this(ActorSystem("FDUnixServerSocketConfigurationSuite"))

  override def afterAll() = {
    TestKit.shutdownActorSystem(system)
  }

  "The FDUnixServerSocketConfiguration" should "read correctly the values from a resource file" in {
    val configuration1 = new FDUnixServerSocketConfiguration(None)
    val configuration2 = new FDUnixServerSocketConfiguration(Some("libpfm"))

    configuration1.controlFlowServerPath should equal("control-flow-server-test.sock")
    configuration1.fdFlowServerPath should equal("fd-flow-server-test.sock")
    configuration2.controlFlowServerPath should equal("control-flow-server.sock")
    configuration2.fdFlowServerPath should equal("fd-flow-server.sock")
  }
}
