package com.service

interface EntityLookupService {
    fun getEntity(): String
    fun exists(id: Any): Boolean
}