package codacy

import codacy.dockerApi.DockerEngine
import codacy.bandit.Bandit

object Engine extends DockerEngine(Bandit)
