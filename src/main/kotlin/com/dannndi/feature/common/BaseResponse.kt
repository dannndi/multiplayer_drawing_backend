package com.dannndi.feature.common

import kotlinx.serialization.Serializable

@Serializable
data class BaseResponse<T>(
    val status: Int = 200,
    val message: String = "",
    val data: T? = null,
)