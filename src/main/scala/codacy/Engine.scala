package codacy

import codacy.bandit.Bandit
import com.codacy.tools.scala.seed.DockerEngine

object Engine extends DockerEngine(Bandit)()
