/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

val kamonCore         = "io.kamon"                  %%  "kamon-core"            % "1.0.1"
val riemannClient     = "com.aphyr"                 %   "riemann-java-client"   % "0.4.1"

name := "kamon-riemann"
resolvers += "clojars" at "https://clojars.org/repo"
testGrouping in Test := singleTestPerJvm((definedTests in Test).value, (javaOptions in Test).value)
libraryDependencies ++=
  compileScope(kamonCore, akkaDependency("actor").value, riemannClient) ++
  testScope(scalatest, akkaDependency("testkit").value, slf4jApi, slf4jnop)

crossScalaVersions := Seq("2.11.12", "2.12.6")

import sbt.Tests._
def singleTestPerJvm(tests: Seq[TestDefinition], jvmSettings: Seq[String]): Seq[Group] =
  tests map { test =>
    Group(
      name = test.name,
      tests = Seq(test),
      runPolicy = SubProcess(ForkOptions(runJVMOptions = jvmSettings)))
  }
