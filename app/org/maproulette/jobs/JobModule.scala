// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

/**
  * @author cuthbertm
  */
class JobModule extends AbstractModule with AkkaGuiceSupport {
  def configure() : Unit = {
    bindActor[SchedulerActor]("scheduler-actor")
    bindActor[ChallengeSchedulerActor]("challenge-scheduler-actor")
    bindActor[LocationSchedulerActor]("location-scheduler-actor")
    bind(classOf[Scheduler]).asEagerSingleton()
  }
}
