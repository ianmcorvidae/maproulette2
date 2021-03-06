// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import akka.actor.Actor
import play.api.Logger
import play.api.db.Database

/**
  * This job will update the challenges from remote geojson or overpass query based on the supplied
  * schedules in the challenge
  *
  * @author cuthbertm
  */
@Singleton
class ChallengeSchedulerActor @Inject() (db:Database) extends Actor {
  override def receive: Receive = {
    case "runChallengeSchedules" => this.runChallengeSchedules()
  }

  def runChallengeSchedules() : Unit = {
    Logger.debug("Running the challenge schedules job now...")
  }
}
