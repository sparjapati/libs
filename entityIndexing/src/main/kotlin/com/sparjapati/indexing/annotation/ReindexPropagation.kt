package com.sparjapati.indexing.annotation

enum class ReindexPropagation {
    /** Join the active scope if one exists; start a new one otherwise. */
    REQUIRED,

    /** Always start a new independent scope, suspending the active one until this scope completes. */
    REQUIRES_NEW,
}
