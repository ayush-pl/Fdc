package com.fdc.nutrition.domain.calc

import java.util.UUID

object Ids { fun new(): String = UUID.randomUUID().toString() }
