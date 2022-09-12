package com.duncpro.festivalgarden.restapi

import com.duncpro.festivalgarden.dbmodels.PerformingArtist
import com.duncpro.festivalgarden.interchange.InterchangeArtist

fun PerformingArtist.toInterchangeArtist(): InterchangeArtist {
    return InterchangeArtist(
        name = this.name,
        smallestImageUrl = this.smallestImageUrl
    )
}