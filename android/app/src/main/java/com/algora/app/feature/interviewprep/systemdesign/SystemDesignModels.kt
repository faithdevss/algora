package com.algora.app.feature.interviewprep.systemdesign

// A step in the "how to drive a system-design round" framework.
data class SystemDesignStep(val title: String, val detail: String)

// A reusable building block, with when to reach for it.
data class SystemDesignConcept(
    val name: String,
    val category: String,     // Scaling, Storage, Reliability, Networking
    val summary: String,
    val whenToUse: String,
)

data class SystemDesignPrimer(
    val id: String,
    val title: String,
    val description: String,
    val framework: List<SystemDesignStep>,
    val concepts: List<SystemDesignConcept>,
)
